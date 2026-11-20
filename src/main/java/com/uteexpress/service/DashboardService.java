package com.uteexpress.service;

import com.uteexpress.dto.admin.DashboardChartsResponse;
import com.uteexpress.dto.admin.DashboardChartsResponse.DailyMetricDto;
import com.uteexpress.dto.admin.DashboardChartsResponse.HubPerformanceDto;
import com.uteexpress.dto.admin.DashboardChartsResponse.StatusBreakdownDto;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.enums.CodStatus;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.repository.HubRepository;
import com.uteexpress.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter LABEL_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");

    private final ShipmentRepository shipmentRepository;
    private final HubRepository hubRepository;

    @Transactional(readOnly = true)
    public DashboardChartsResponse getDashboardCharts(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        LocalDate effectiveTo = (toDate != null) ? toDate : today;
        LocalDate effectiveFrom = (fromDate != null) ? fromDate : effectiveTo.minusDays(29);
        if (effectiveFrom.isAfter(effectiveTo)) {
            LocalDate tmp = effectiveFrom;
            effectiveFrom = effectiveTo;
            effectiveTo = tmp;
        }

        OffsetDateTime startDateTime = effectiveFrom.atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
        OffsetDateTime endDateTime = effectiveTo.plusDays(1).atStartOfDay(VIETNAM_ZONE).toOffsetDateTime().minusNanos(1);

        List<Shipment> shipments = shipmentRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(startDateTime, endDateTime);
        List<Hub> allHubs = hubRepository.findAllByOrderByIdAsc();

        long totalShipments = shipments.size();
        long delivered = 0;
        long inProgress = 0;
        long failedOrReturned = 0;
        long cancelled = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal pendingCodAmount = BigDecimal.ZERO;

        // Continuous date buckets
        Map<LocalDate, DailyAccumulator> dailyMap = new LinkedHashMap<>();
        for (LocalDate d = effectiveFrom; !d.isAfter(effectiveTo); d = d.plusDays(1)) {
            dailyMap.put(d, new DailyAccumulator());
        }

        // Hub accumulators
        Map<Long, HubAccumulator> hubMap = new LinkedHashMap<>();
        for (Hub hub : allHubs) {
            hubMap.put(hub.getId(), new HubAccumulator(hub.getCode(), hub.getName()));
        }

        for (Shipment s : shipments) {
            ShipmentStatus st = s.getStatus();
            BigDecimal fee = s.getTotalFee() != null ? s.getTotalFee() : BigDecimal.ZERO;

            if (st == ShipmentStatus.DELIVERED) {
                delivered++;
            } else if (st == ShipmentStatus.CANCELLED) {
                cancelled++;
            } else if (st == ShipmentStatus.DELIVERY_FAILED || st == ShipmentStatus.RETURNING || st == ShipmentStatus.RETURNED) {
                failedOrReturned++;
            } else {
                inProgress++;
            }

            boolean countsAsRevenue = (st != ShipmentStatus.CANCELLED);
            if (countsAsRevenue) {
                totalRevenue = totalRevenue.add(fee);
            }

            if (st == ShipmentStatus.DELIVERED
                    && (s.getCodStatus() == CodStatus.HELD_BY_SHIPPER || s.getCodStatus() == CodStatus.COLLECTED_BY_HUB)
                    && s.getCodAmount() != null) {
                pendingCodAmount = pendingCodAmount.add(s.getCodAmount());
            }

            if (s.getCreatedAt() != null) {
                LocalDate createdDay = s.getCreatedAt().atZoneSameInstant(VIETNAM_ZONE).toLocalDate();
                DailyAccumulator acc = dailyMap.get(createdDay);
                if (acc != null) {
                    acc.count++;
                    if (countsAsRevenue) {
                        acc.revenue = acc.revenue.add(fee);
                    }
                }
            }

            if (s.getOriginHub() != null && hubMap.containsKey(s.getOriginHub().getId())) {
                HubAccumulator originAcc = hubMap.get(s.getOriginHub().getId());
                originAcc.originCount++;
                if (countsAsRevenue) {
                    originAcc.revenue = originAcc.revenue.add(fee);
                }
            }
            if (s.getDestHub() != null && hubMap.containsKey(s.getDestHub().getId())) {
                HubAccumulator destAcc = hubMap.get(s.getDestHub().getId());
                destAcc.destCount++;
                if (st == ShipmentStatus.DELIVERED) {
                    destAcc.deliveredCount++;
                }
            }
        }

        long nonCancelled = totalShipments - cancelled;
        double successRate = nonCancelled > 0
                ? Math.round((delivered * 1000.0) / nonCancelled) / 10.0
                : 0.0;

        List<DailyMetricDto> dailyMetrics = dailyMap.entrySet().stream()
                .map(e -> new DailyMetricDto(
                        e.getKey().format(LABEL_DATE_FMT),
                        e.getValue().count,
                        e.getValue().revenue
                ))
                .toList();

        List<HubPerformanceDto> hubPerformance = hubMap.values().stream()
                .map(h -> new HubPerformanceDto(
                        h.hubCode,
                        h.hubName,
                        h.originCount,
                        h.destCount,
                        h.deliveredCount,
                        h.revenue
                ))
                .sorted(Comparator.comparingLong((HubPerformanceDto h) -> h.originCount() + h.destCount()).reversed())
                .toList();

        return new DashboardChartsResponse(
                effectiveFrom.toString(),
                effectiveTo.toString(),
                totalShipments,
                inProgress,
                delivered,
                failedOrReturned,
                cancelled,
                totalRevenue,
                successRate,
                pendingCodAmount,
                dailyMetrics,
                new StatusBreakdownDto(delivered, inProgress, failedOrReturned, cancelled),
                hubPerformance
        );
    }

    private static class DailyAccumulator {
        long count = 0;
        BigDecimal revenue = BigDecimal.ZERO;
    }

    private static class HubAccumulator {
        final String hubCode;
        final String hubName;
        long originCount = 0;
        long destCount = 0;
        long deliveredCount = 0;
        BigDecimal revenue = BigDecimal.ZERO;

        HubAccumulator(String hubCode, String hubName) {
            this.hubCode = hubCode;
            this.hubName = hubName;
        }
    }
}
