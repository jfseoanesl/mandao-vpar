package com.mandao.couriers;

import com.mandao.couriers.CourierDtos.CourierView;
import com.mandao.couriers.CourierDtos.DocumentView;
import com.mandao.couriers.CourierDtos.UpdateCourierRequest;
import com.mandao.couriers.CourierEnums.CourierStatus;
import com.mandao.couriers.CourierEnums.DocumentType;
import com.mandao.evidence.FileInspector;
import com.mandao.evidence.StorageService;
import com.mandao.identity.User;
import com.mandao.identity.UserRepository;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CourierService {

    public record DocumentFile(byte[] data, String contentType) {
    }

    private final CourierProfileRepository profiles;
    private final CourierDocumentRepository documents;
    private final UserRepository users;
    private final StorageService storage;
    private final AuditService audit;

    public CourierService(CourierProfileRepository profiles, CourierDocumentRepository documents,
                          UserRepository users, StorageService storage, AuditService audit) {
        this.profiles = profiles;
        this.documents = documents;
        this.users = users;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public CourierView view(UUID courierId) {
        CourierProfile profile = profile(courierId);
        User user = users.findById(courierId).orElseThrow(() -> ApiException.notFound("Mandadero"));
        List<CourierDocument> docs = documents.findByCourierId(courierId);
        Set<DocumentType> uploaded = docs.stream().map(CourierDocument::getType).collect(Collectors.toSet());
        List<DocumentType> missing = DocumentType.requiredFor(profile.getVehicleType()).stream()
                .filter(t -> !uploaded.contains(t)).sorted().toList();
        List<DocumentView> docViews = docs.stream()
                .sorted(Comparator.comparing(CourierDocument::getType))
                .map(d -> new DocumentView(d.getId(), d.getType(), d.getUploadedAt())).toList();
        return new CourierView(courierId, user.getFullName(), user.getPhone(), profile.getStatus(),
                profile.getRejectionReason(), profile.getVehicleType(), profile.getPlate(), profile.getPayoutMethod(),
                profile.getPayoutAccount(), profile.isAvailable(), profile.getLat(), profile.getLng(),
                profile.getLastPingAt(), profile.getApprovedAt(), docViews, missing);
    }

    @Transactional
    public CourierView updateDetails(UUID courierId, UpdateCourierRequest req) {
        CourierProfile profile = profile(courierId);
        String plate = req.plate() == null || req.plate().isBlank() ? null : req.plate().toUpperCase();
        if (req.vehicleType().motorized() && plate == null) {
            throw ApiException.badRequest("La placa es obligatoria para moto o carro");
        }
        profile.updateDetails(req.vehicleType(), plate, req.payoutMethod(), req.payoutAccount());
        return view(courierId);
    }

    @Transactional
    public CourierView uploadDocument(UUID courierId, DocumentType type, MultipartFile file) {
        CourierProfile profile = profile(courierId);
        if (profile.getStatus() == CourierStatus.APPROVED) {
            throw ApiException.conflict("Tu cuenta ya está aprobada. Contacta a soporte para cambiar documentos.");
        }
        FileInspector.CleanFile clean = FileInspector.inspect(file, true);
        CourierDocument doc = documents.findByCourierIdAndType(courierId, type)
                .orElseGet(() -> new CourierDocument(courierId, type));
        String oldKey = doc.getObjectKey();
        doc.replaceFile(storage.put(clean.data(), clean.contentType(), "documents/" + courierId, clean.extension()),
                clean.contentType());
        documents.save(doc);
        if (oldKey != null) {
            storage.delete(oldKey);
        }
        if (profile.getStatus() == CourierStatus.REJECTED) {
            // Al corregir documentos vuelve a la cola de revisión.
            profile.resubmit();
        }
        return view(courierId);
    }

    @Transactional
    public CourierView setAvailability(UUID courierId, boolean available) {
        CourierProfile profile = profile(courierId);
        if (available && !profile.isApproved()) {
            throw ApiException.conflict("Tu cuenta aún no ha sido aprobada");
        }
        profile.setAvailable(available);
        return view(courierId);
    }

    @Transactional(readOnly = true)
    public List<CourierView> list(CourierStatus status) {
        List<CourierProfile> list = status == null ? profiles.findAll() : profiles.findByStatus(status);
        return list.stream().map(p -> view(p.getUserId())).toList();
    }

    @Transactional
    public CourierView approve(UUID courierId, UUID adminId) {
        CourierProfile profile = profile(courierId);
        CourierView current = view(courierId);
        if (profile.getVehicleType() == null || profile.getPayoutAccount() == null) {
            throw ApiException.unprocessable("El mandadero no ha completado vehículo y cuenta de pago");
        }
        if (!current.missingDocuments().isEmpty()) {
            throw ApiException.unprocessable("Faltan documentos: " + current.missingDocuments());
        }
        profile.approve();
        audit.record(adminId, "COURIER_APPROVED", "courier", courierId, null);
        return view(courierId);
    }

    @Transactional
    public CourierView reject(UUID courierId, String reason, UUID adminId) {
        profile(courierId).reject(reason);
        audit.record(adminId, "COURIER_REJECTED", "courier", courierId, reason);
        return view(courierId);
    }

    @Transactional(readOnly = true)
    public DocumentFile documentFile(UUID documentId) {
        CourierDocument doc = documents.findById(documentId).orElseThrow(() -> ApiException.notFound("Documento"));
        return new DocumentFile(storage.get(doc.getObjectKey()), doc.getContentType());
    }

    public CourierProfile profile(UUID courierId) {
        return profiles.findById(courierId).orElseThrow(() -> ApiException.notFound("Mandadero"));
    }
}
