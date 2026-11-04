package com.myroutine.member.domain;

import com.myroutine.common.model.Ids;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Getter
@Table(name = "inquiry_answer", schema = "member")
@EntityListeners(AuditingEntityListener.class)
public class InquiryAnswer {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "inquiry_id", nullable = false)
    private UUID inquiryId;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "content", nullable = false)
    private String content;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static InquiryAnswer write(UUID inquiryId, UUID adminId, String content) {
        InquiryAnswer inquiryAnswer = new InquiryAnswer();
        inquiryAnswer.id = Ids.newId();
        inquiryAnswer.inquiryId = inquiryId;
        inquiryAnswer.adminId = adminId;
        inquiryAnswer.content = content;
        return inquiryAnswer;
    }
}
