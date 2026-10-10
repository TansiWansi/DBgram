package com.dbgram.domain.user;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for the Follow entity.
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FollowId implements Serializable {
    private UUID followerId;
    private UUID followingId;
}
