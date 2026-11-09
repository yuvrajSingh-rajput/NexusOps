package com.nexusops.shared.sequence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SequenceGeneratorService {

    private final TenantSequenceRepository sequenceRepository;

    public SequenceGeneratorService(TenantSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized String generateNextSequenceNumber(UUID organizationId, String entityType, String prefix) {
        TenantSequenceId seqId = new TenantSequenceId(organizationId, entityType.toUpperCase());
        TenantSequenceEntity seq = sequenceRepository.findById(seqId)
                .orElseGet(() -> sequenceRepository.save(new TenantSequenceEntity(seqId, 1000L)));

        Long nextVal = seq.incrementAndGet();
        sequenceRepository.save(seq);
        return prefix.toUpperCase() + "-" + nextVal;
    }
}

