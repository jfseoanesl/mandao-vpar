package com.mandao.companies;

import com.mandao.companies.CompanyService.AddressRequest;
import com.mandao.companies.CompanyService.AddressView;
import com.mandao.companies.CompanyService.CompanyView;
import com.mandao.companies.CompanyService.CreateCompanyRequest;
import com.mandao.companies.CompanyService.MemberRequest;
import com.mandao.identity.AuthDtos.UserView;
import com.mandao.orders.ErrandDtos.ErrandSummary;
import com.mandao.shared.AuthUser;
import com.mandao.shared.FileResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
public class CompanyController {

    private final CompanyService service;

    public CompanyController(CompanyService service) {
        this.service = service;
    }

    // ---- Operador ----

    @GetMapping("/api/admin/companies")
    public List<CompanyView> list() {
        return service.list();
    }

    @PostMapping("/api/admin/companies")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyView create(@Valid @RequestBody CreateCompanyRequest request) {
        return service.create(request, AuthUser.current().id());
    }

    // ---- Administrador de empresa ----

    @GetMapping("/api/company")
    public CompanyView mine() {
        return service.mine(AuthUser.current());
    }

    @GetMapping("/api/company/members")
    public List<UserView> members() {
        return service.members(AuthUser.current());
    }

    @PostMapping("/api/company/members")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView addMember(@Valid @RequestBody MemberRequest request) {
        return service.addMember(request, AuthUser.current());
    }

    @GetMapping("/api/company/errands")
    public List<ErrandSummary> errands() {
        return service.errands(AuthUser.current());
    }

    @PostMapping("/api/company/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressView addAddress(@Valid @RequestBody AddressRequest request) {
        return service.addAddress(request, AuthUser.current());
    }

    @DeleteMapping("/api/company/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@PathVariable("id") UUID id) {
        service.deleteAddress(id, AuthUser.current());
    }

    @GetMapping("/api/company/report")
    public ResponseEntity<byte[]> report(@RequestParam("month") String month) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (Exception e) {
            throw com.mandao.shared.ApiException.badRequest("Mes inválido, usa AAAA-MM");
        }
        return FileResponses.csv(service.monthlyReport(ym, AuthUser.current()), "mandao-reporte-" + ym + ".csv");
    }

    // ---- Cualquier miembro ----

    @GetMapping("/api/me/addresses")
    public List<AddressView> myAddresses() {
        return service.addressesFor(AuthUser.current());
    }
}
