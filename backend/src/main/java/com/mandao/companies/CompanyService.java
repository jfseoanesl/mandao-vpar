package com.mandao.companies;

import com.mandao.identity.AuthDtos;
import com.mandao.identity.AuthDtos.UserView;
import com.mandao.identity.Role;
import com.mandao.identity.User;
import com.mandao.identity.UserRepository;
import com.mandao.orders.Errand;
import com.mandao.orders.ErrandDtos.ErrandSummary;
import com.mandao.orders.ErrandRepository;
import com.mandao.orders.ErrandViews;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuditService;
import com.mandao.shared.AuthUser;
import com.mandao.shared.FileResponses;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Mandao Empresas (spec 011). */
@Service
public class CompanyService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(BOGOTA);

    public record CreateCompanyRequest(
            @NotBlank @Size(max = 160) String name,
            @Pattern(regexp = "^$|^[0-9-]{5,20}$", message = "NIT inválido") String nit,
            @NotNull Company.Plan plan,
            @NotBlank @Size(min = 3, max = 120) String adminFullName,
            @NotBlank @Pattern(regexp = AuthDtos.PHONE_REGEX, message = "Celular inválido") String adminPhone,
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_REGEX, message = AuthDtos.PASSWORD_MSG) String adminPassword) {
    }

    public record MemberRequest(
            @NotBlank @Size(min = 3, max = 120) String fullName,
            @NotBlank @Pattern(regexp = AuthDtos.PHONE_REGEX, message = "Celular inválido") String phone,
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_REGEX, message = AuthDtos.PASSWORD_MSG) String password) {
    }

    public record AddressRequest(
            @NotBlank @Size(max = 60) String label,
            @NotBlank @Size(max = 250) String address,
            @DecimalMin("-90") @DecimalMax("90") double lat,
            @DecimalMin("-180") @DecimalMax("180") double lng) {
    }

    public record CompanyView(UUID id, String name, String nit, Company.Plan plan, long monthlyFee, boolean active,
                              Instant createdAt, long members) {
    }

    public record AddressView(UUID id, String label, String address, double lat, double lng) {
        static AddressView of(SavedAddress a) {
            return new AddressView(a.getId(), a.getLabel(), a.getAddress(), a.getLat(), a.getLng());
        }
    }

    private final CompanyRepository companies;
    private final SavedAddressRepository addresses;
    private final UserRepository users;
    private final ErrandRepository errands;
    private final ErrandViews views;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public CompanyService(CompanyRepository companies, SavedAddressRepository addresses, UserRepository users,
                          ErrandRepository errands, ErrandViews views, PasswordEncoder encoder, AuditService audit) {
        this.companies = companies;
        this.addresses = addresses;
        this.users = users;
        this.errands = errands;
        this.views = views;
        this.encoder = encoder;
        this.audit = audit;
    }

    /** AC-011-01. */
    @Transactional
    public CompanyView create(CreateCompanyRequest req, UUID adminId) {
        if (users.existsByPhone(req.adminPhone())) {
            throw ApiException.conflict("El celular del administrador ya está registrado");
        }
        Company company = companies.save(new Company(req.name().trim(), blank(req.nit()), req.plan()));
        users.save(new User(req.adminFullName().trim(), req.adminPhone(), null, encoder.encode(req.adminPassword()),
                Role.COMPANY_ADMIN, company.getId(), "b2b"));
        audit.record(adminId, "COMPANY_CREATED", "company", company.getId(), company.getName());
        return view(company);
    }

    @Transactional(readOnly = true)
    public List<CompanyView> list() {
        return companies.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public CompanyView mine(AuthUser user) {
        return view(company(user));
    }

    @Transactional(readOnly = true)
    public List<UserView> members(AuthUser user) {
        return users.findByCompanyIdOrderByCreatedAtAsc(company(user).getId()).stream().map(UserView::of).toList();
    }

    /** AC-011-02. */
    @Transactional
    public UserView addMember(MemberRequest req, AuthUser user) {
        Company company = company(user);
        if (users.existsByPhone(req.phone())) {
            throw ApiException.conflict("Este celular ya está registrado en Mandao");
        }
        User member = users.save(new User(req.fullName().trim(), req.phone(), null, encoder.encode(req.password()),
                Role.CLIENT, company.getId(), "b2b"));
        audit.record(user.id(), "COMPANY_MEMBER_ADDED", "company", company.getId(), member.getId().toString());
        return UserView.of(member);
    }

    /** AC-011-04. */
    @Transactional(readOnly = true)
    public List<ErrandSummary> errands(AuthUser user) {
        return errands.findByCompanyIdOrderByCreatedAtDesc(company(user).getId(), PageRequest.of(0, 200)).stream()
                .map(views::summary).toList();
    }

    /** AC-011-05: lectura para cualquier miembro. */
    @Transactional(readOnly = true)
    public List<AddressView> addressesFor(AuthUser user) {
        if (user.companyId() == null) {
            return List.of();
        }
        return addresses.findByCompanyIdOrderByLabelAsc(user.companyId()).stream().map(AddressView::of).toList();
    }

    @Transactional
    public AddressView addAddress(AddressRequest req, AuthUser user) {
        Company company = company(user);
        return AddressView.of(addresses.save(new SavedAddress(company.getId(), req.label().trim(),
                req.address().trim(), req.lat(), req.lng())));
    }

    @Transactional
    public void deleteAddress(UUID id, AuthUser user) {
        SavedAddress address = addresses.findById(id)
                .filter(a -> a.getCompanyId().equals(user.companyId()))
                .orElseThrow(() -> ApiException.notFound("Dirección"));
        addresses.delete(address);
    }

    /** AC-011-06. */
    @Transactional(readOnly = true)
    public String monthlyReport(YearMonth month, AuthUser user) {
        Company company = company(user);
        Instant from = month.atDay(1).atStartOfDay(BOGOTA).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(BOGOTA).toInstant();
        List<Errand> list = errands.findByCompanyIdAndCreatedAtBetweenOrderByCreatedAtAsc(company.getId(), from, to);
        Map<UUID, User> people = users.findAllById(list.stream()
                        .flatMap(e -> java.util.stream.Stream.of(e.getClientId(), e.getCourierId()))
                        .filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        StringBuilder sb = new StringBuilder("fecha,codigo,solicitante,categoria,estado,precio,mandadero,descripcion\n");
        for (Errand e : list) {
            User requester = people.get(e.getClientId());
            User courier = e.getCourierId() == null ? null : people.get(e.getCourierId());
            sb.append(String.join(",",
                    FileResponses.csvCell(DATE.format(e.getCreatedAt())),
                    FileResponses.csvCell(e.getCode()),
                    FileResponses.csvCell(requester == null ? null : requester.getFullName()),
                    FileResponses.csvCell(e.getCategory()),
                    FileResponses.csvCell(e.getStatus()),
                    FileResponses.csvCell(e.getTotalPrice()),
                    FileResponses.csvCell(courier == null ? null : courier.getFullName()),
                    FileResponses.csvCell(e.getDescription())));
            sb.append('\n');
        }
        return sb.toString();
    }

    private Company company(AuthUser user) {
        if (user.companyId() == null) {
            throw ApiException.notFound("Empresa");
        }
        return companies.findById(user.companyId()).orElseThrow(() -> ApiException.notFound("Empresa"));
    }

    private CompanyView view(Company c) {
        long members = users.findByCompanyIdOrderByCreatedAtAsc(c.getId()).size();
        return new CompanyView(c.getId(), c.getName(), c.getNit(), c.getPlan(), c.getMonthlyFee(), c.isActive(),
                c.getCreatedAt(), members);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
