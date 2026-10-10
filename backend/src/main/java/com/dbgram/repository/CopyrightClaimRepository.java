package com.dbgram.repository;

import com.dbgram.domain.post.CopyrightClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CopyrightClaimRepository extends JpaRepository<CopyrightClaim, UUID> {
}
