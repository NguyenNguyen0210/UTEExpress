package com.uteexpress.service;

import com.uteexpress.dto.admin.DashboardChartsResponse;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.enums.CodStatus;
import com.uteexpress.enums.HubType;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.repository.HubRepository;
import com.uteexpress.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private HubRepository hubRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(shipmentRepository, hubRepository);
    }

    @Test
    void shouldAggregateKpisDailySeriesStatusBreakdownAndHubPerformance() {
        Hub hcm = Hub.builder().id(1L).code("HCM-TD01").name("Bưu cục Thủ Đức").type(HubType.POST_OFFICE).build();
        Hub hn = Hub.builder().id(2L).code("HN-CG01").name("Bưu cục Cầu Giấy").type(HubType.POST_OFFICE).build();

        LocalDate today = LocalDate.of(2026, 11, 20);
        OffsetDateTime ts = today.atTime(10, 0).atOffset(ZoneOffset.ofHours(7));

        Shipment s1 = Shipment.builder()
                .originHub(hcm).destHub(hn)
                .status(ShipmentStatus.DELIVERED)
                .codStatus(CodStatus.HELD_BY_SHIPPER)
                .totalFee(new BigDecimal("50000"))
                .codAmount(new BigDecimal("500000"))
                .createdAt(ts)
                .build();

        Shipment s2 = Shipment.builder()
                .originHub(hcm).destHub(hcm)
                .status(ShipmentStatus.IN_TRANSIT)
                .codStatus(CodStatus.PENDING)
                .totalFee(new BigDecimal("30000"))
                .codAmount(new BigDecimal("200000"))
                .createdAt(ts)
                .build();

        Shipment s3 = Shipment.builder()
                .originHub(hn).destHub(hcm)
                .status(ShipmentStatus.CANCELLED)
                .codStatus(CodStatus.NONE)
                .totalFee(new BigDecimal("99000"))
                .codAmount(BigDecimal.ZERO)
                .createdAt(ts)
                .build();

        when(hubRepository.findAllByOrderByIdAsc()).thenReturn(List.of(hcm, hn));
        when(shipmentRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(s1, s2, s3));

        DashboardChartsResponse res = dashboardService.getDashboardCharts(today.minusDays(6), today);

        assertThat(res.totalShipments()).isEqualTo(3);
        assertThat(res.deliveredShipments()).isEqualTo(1);
        assertThat(res.inProgressShipments()).isEqualTo(1);
        assertThat(res.cancelledShipments()).isEqualTo(1);
        // Cancelled shipment fee (99k) excluded -> 50k + 30k = 80k
        assertThat(res.totalRevenue()).isEqualByComparingTo("80000");
        // 1 delivered out of 2 non-cancelled = 50.0%
        assertThat(res.deliverySuccessRate()).isEqualTo(50.0);
        // Pending COD from s1 (DELIVERED + HELD_BY_SHIPPER)
        assertThat(res.pendingCodAmount()).isEqualByComparingTo("500000");
        // Continuous 7-day buckets
        assertThat(res.dailyMetrics()).hasSize(7);
        assertThat(res.hubPerformance()).hasSize(2);
    }
}
