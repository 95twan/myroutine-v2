package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.web.Cursor;
import com.myroutine.common.web.CursorPage;
import com.myroutine.member.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InquiryService {
    private final InquiryRepository inquiryRepository;
    private final InquiryAnswerRepository inquiryAnswerRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public UUID write(UUID memberId, String title, String content) {
        Inquiry inquiry = Inquiry.write(memberId, title, content);
        Inquiry saved = inquiryRepository.save(inquiry);
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public CursorPage<InquiryResult> getMyInquiries(UUID memberId, String cursor, int size) {
        List<Inquiry> inquiries;
        if (cursor == null || cursor.isBlank()) {
            inquiries = inquiryRepository.findByMemberFirstPage(memberId, Limit.of(size + 1));
        } else {
            Cursor current = Cursor.decode(cursor);
            inquiries = inquiryRepository.findByMemberNextPage(memberId, current.createdAt(), current.id(), Limit.of(size + 1));
        }

        String nextCursor = null;
        if (inquiries.size() > size) {
            inquiries = inquiries.subList(0, size);
            Inquiry lastInquiry = inquiries.getLast();
            nextCursor = new Cursor(lastInquiry.getCreatedAt(), lastInquiry.getId()).encode();
        }

        List<UUID> inquiryIds = inquiries.stream()
                .map(Inquiry::getId)
                .toList();

        Map<UUID, InquiryAnswer> inquiryAnswerMap = inquiryAnswerRepository.findByInquiryIdIn(inquiryIds).stream()
                .collect(Collectors.toMap(InquiryAnswer::getInquiryId, Function.identity()));

        List<InquiryResult> items = inquiries.stream()
                .map(inquiry -> InquiryResult.from(inquiry, inquiryAnswerMap.get(inquiry.getId())))
                .toList();

        return new CursorPage<>(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public InquiryDetailResult getMyInquiry(UUID memberId, UUID inquiryId) {
        Inquiry inquiry = inquiryRepository.findByIdAndMemberId(inquiryId, memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.INQUIRY_NOT_FOUND)
        );

        InquiryAnswer inquiryAnswer = inquiryAnswerRepository.findByInquiryId(inquiryId).orElse(null);

        return InquiryDetailResult.from(inquiry, inquiryAnswer);
    }

    @Transactional(readOnly = true)
    public CursorPage<AdminInquiryResult> getForAdmin(InquiryStatus status, String cursor, int size) {
        List<Inquiry> inquiries;
        if (cursor == null || cursor.isBlank()) {
            if (status == null) {
                inquiries = inquiryRepository.findAllFirstPage(Limit.of(size + 1));
            } else {
                inquiries = inquiryRepository.findByStatusFirstPage(status, Limit.of(size + 1));
            }
        } else {
            Cursor current = Cursor.decode(cursor);
            if (status == null) {
                inquiries = inquiryRepository.findAllNextPage(current.createdAt(), current.id(), Limit.of(size + 1));
            } else {
                inquiries = inquiryRepository.findByStatusNextPage(status, current.createdAt(), current.id(), Limit.of(size + 1));
            }
        }

        String nextCursor = null;
        if (inquiries.size() > size) {
            inquiries = inquiries.subList(0, size);
            Inquiry lastInquiry = inquiries.getLast();
            nextCursor = new Cursor(lastInquiry.getCreatedAt(), lastInquiry.getId()).encode();
        }

        List<UUID> inquiryIds = inquiries.stream()
                .map(Inquiry::getId)
                .toList();

        List<UUID> memberIds = inquiries.stream()
                .map(Inquiry::getMemberId)
                .distinct()
                .toList();

        Map<UUID, InquiryAnswer> inquiryAnswerMap = inquiryAnswerRepository.findByInquiryIdIn(inquiryIds).stream()
                .collect(Collectors.toMap(InquiryAnswer::getInquiryId, Function.identity()));

        Map<UUID, Member> memberMap = memberRepository.findAllByIdIn(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        List<AdminInquiryResult> items = inquiries.stream()
                .map(inquiry -> {
                    InquiryAnswer answer = inquiryAnswerMap.get(inquiry.getId());
                    Member member = memberMap.get(inquiry.getMemberId());
                    return AdminInquiryResult.from(inquiry, answer, member);
                })
                .toList();

        return new CursorPage<>(items, nextCursor);
    }

    @Transactional
    public void answer(UUID adminId, UUID inquiryId, String content) {
        Inquiry inquiry = inquiryRepository.findByIdForUpdate(inquiryId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.INQUIRY_NOT_FOUND)
        );
        InquiryAnswer answer = inquiry.answer(adminId, content);
        inquiryAnswerRepository.save(answer);
    }

    @Transactional
    public InquiryDetailResult edit(UUID memberId, UUID inquiryId, EditInquiryCommand command) {
        Inquiry inquiry = inquiryRepository.findByIdAndMemberId(inquiryId, memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.INQUIRY_NOT_FOUND)
        );
        inquiry.edit(command.title(), command.content());
        return InquiryDetailResult.from(inquiry, null);
    }

    @Transactional
    public void delete(UUID memberId, UUID inquiryId) {
        Inquiry inquiry = inquiryRepository.findByIdAndMemberId(inquiryId, memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.INQUIRY_NOT_FOUND)
        );
        inquiry.validateEditable();
        inquiryRepository.delete(inquiry);
    }
}
