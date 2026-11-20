package com.uteexpress.controller.web;

import com.uteexpress.dto.publicview.PublicTrackingResponse;
import com.uteexpress.enums.HubType;
import com.uteexpress.service.HubService;
import com.uteexpress.service.PublicTrackingService;
import com.uteexpress.service.ServiceTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PublicController {

    private final ServiceTypeService serviceTypeService;
    private final HubService hubService;
    private final PublicTrackingService publicTrackingService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("serviceTypes", serviceTypeService.findActiveServices());
        model.addAttribute("hubCount", hubService.findActiveHubs().size());
        model.addAttribute("provinceCount", hubService.getActiveProvinces().size());
        return "public/home";
    }

    @GetMapping("/tracking")
    public String tracking(@RequestParam(required = false) String codes, Model model) {
        String normalizedInput = codes != null ? codes.trim() : "";
        model.addAttribute("codes", normalizedInput);
        if (!normalizedInput.isEmpty()) {
            PublicTrackingResponse result = publicTrackingService.lookupByCodes(normalizedInput);
            model.addAttribute("trackingResult", result);
            model.addAttribute("hasSearched", true);
        } else {
            model.addAttribute("hasSearched", false);
        }
        return "public/tracking";
    }

    @GetMapping("/estimate")
    public String estimate(Model model) {
        model.addAttribute("provinces", hubService.getActiveProvinces());
        model.addAttribute("serviceTypes", serviceTypeService.findActiveServices());
        return "public/estimate";
    }

    @GetMapping("/hubs")
    public String hubs(@RequestParam(required = false) String province,
                       @RequestParam(required = false) HubType type,
                       Model model) {
        model.addAttribute("hubs", hubService.searchPublicHubs(province, type));
        model.addAttribute("provinces", hubService.getActiveProvinces());
        model.addAttribute("hubTypes", HubType.values());
        model.addAttribute("selectedProvince", province);
        model.addAttribute("selectedType", type);
        return "public/hubs";
    }
}
