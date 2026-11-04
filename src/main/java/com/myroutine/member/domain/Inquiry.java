package com.myroutine.member.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Ids;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Getter
@Table(name = "inquiry", schema = "member")
public class Inquiry extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InquiryStatus status;

    @Version
    @Column(name = "version")
    private Long version;

    public static Inquiry write(UUID memberId, String title, String content) {
        Inquiry inquiry = new Inquiry();
        inquiry.id = Ids.newId();
        inquiry.memberId = memberId;
        inquiry.title = title;
        inquiry.content = content;
        inquiry.status = InquiryStatus.OPEN;
        return inquiry;
    }

    public InquiryAnswer answer(UUID adminId, String content) {
        this.status = this.status.transitTo(InquiryStatus.ANSWERED);
        InquiryAnswer inquiryAnswer = InquiryAnswer.write(this.id, adminId, content);
        return inquiryAnswer;
    }

    public void edit(String title, String content) {
        validateEditable();
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
    }

    public void validateEditable() {
        if (this.status != InquiryStatus.OPEN) {
            throw new BusinessException(MemberErrorCode.INQUIRY_ALREADY_ANSWERED);
        }
    }
}
