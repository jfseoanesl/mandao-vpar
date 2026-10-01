package com.mandao.evidence;

import com.mandao.orders.Errand;
import com.mandao.orders.ErrandAccess;
import com.mandao.orders.ErrandStatus;
import com.mandao.payments.SettlementService;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuthUser;
import com.mandao.tracking.RealtimePublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Evidencias del mandado (spec 005). */
@Service
public class EvidenceService {

    public record EvidenceFile(byte[] data, String contentType) {
    }

    public record CourierEvidence(Evidence.Type type, String note, Long amount, Double lat, Double lng) {
    }

    private final EvidenceRepository evidences;
    private final StorageService storage;
    private final SettlementService settlement;
    private final ErrandAccess access;
    private final RealtimePublisher realtime;

    public EvidenceService(EvidenceRepository evidences, StorageService storage, SettlementService settlement,
                           ErrandAccess access, RealtimePublisher realtime) {
        this.evidences = evidences;
        this.storage = storage;
        this.settlement = settlement;
        this.access = access;
        this.realtime = realtime;
    }

    /** AC-005-05, AC-005-06, AC-007-02. */
    @Transactional
    public Evidence addCourierEvidence(UUID errandId, AuthUser courier, CourierEvidence input, MultipartFile file) {
        Errand errand = access.requireAssignedCourier(errandId, courier);
        if (!ErrandStatus.EVIDENCE_ALLOWED.contains(errand.getStatus())) {
            throw ApiException.conflict("En este estado no se pueden agregar evidencias");
        }
        if (input.type() == Evidence.Type.ADJUNTO) {
            throw ApiException.badRequest("Tipo de evidencia no permitido");
        }
        String note = trim(input.note(), 1000);
        String key = null;
        String contentType = null;
        if (input.type() == Evidence.Type.NOTA) {
            if (note == null) {
                throw ApiException.badRequest("La nota no puede estar vacía");
            }
        } else {
            FileInspector.CleanFile clean = FileInspector.inspect(file, input.type() == Evidence.Type.FACTURA);
            key = storage.put(clean.data(), clean.contentType(), "errands/" + errandId, clean.extension());
            contentType = clean.contentType();
        }
        Long amount = null;
        if (input.type() == Evidence.Type.FACTURA && input.amount() != null) {
            amount = input.amount();
            if (errand.getPurchaseBudget() > 0) {
                settlement.recordPurchase(errand, amount);
            }
        }
        Evidence saved = evidences.save(new Evidence(errandId, courier.id(), input.type(), key, contentType, note,
                amount, input.lat(), input.lng()));
        publish(errand, saved);
        return saved;
    }

    @Transactional
    public Evidence addClientAttachment(UUID errandId, AuthUser client, MultipartFile file, String note) {
        Errand errand = access.requireOwner(errandId, client);
        if (ErrandStatus.FINAL.contains(errand.getStatus())) {
            throw ApiException.conflict("El mandado ya terminó");
        }
        FileInspector.CleanFile clean = FileInspector.inspect(file, true);
        String key = storage.put(clean.data(), clean.contentType(), "errands/" + errandId, clean.extension());
        Evidence saved = evidences.save(new Evidence(errandId, client.id(), Evidence.Type.ADJUNTO, key,
                clean.contentType(), trim(note, 1000), null, null, null));
        publish(errand, saved);
        return saved;
    }

    /** AC-005-07: solo dueño, empresa, mandadero asignado o admin. */
    @Transactional(readOnly = true)
    public EvidenceFile file(UUID evidenceId, AuthUser user) {
        Evidence evidence = evidences.findById(evidenceId).orElseThrow(() -> ApiException.notFound("Evidencia"));
        access.resolve(evidence.getErrandId(), user, false);
        if (evidence.getObjectKey() == null) {
            throw ApiException.notFound("Archivo");
        }
        return new EvidenceFile(storage.get(evidence.getObjectKey()), evidence.getContentType());
    }

    private void publish(Errand errand, Evidence evidence) {
        Map<String, Object> data = new HashMap<>();
        data.put("evidenceId", evidence.getId().toString());
        data.put("type", evidence.getType().name());
        realtime.errand(errand.getId(), "EVIDENCE_ADDED", errand.getStatus().name(), data);
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.substring(0, Math.min(max, t.length()));
    }
}
