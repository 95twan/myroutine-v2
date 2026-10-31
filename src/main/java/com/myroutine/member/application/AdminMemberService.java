package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.outbox.OutboxPublisher;
import com.myroutine.common.web.Cursor;
import com.myroutine.common.web.CursorPage;
import com.myroutine.member.api.event.MemberBannedEvent;
import com.myroutine.member.api.event.MemberTopics;
import com.myroutine.member.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final TokenVersionService tokenVersionService;
    private final SessionStore sessionStore;
    private final OutboxPublisher outboxPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CursorPage<AdminMemberResult> search(String query, MemberStatus status, String cursor, int size) {
        String keyword = "";
        if (query != null && !query.isBlank()) {
            keyword = query.strip().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
        }
        List<Member> members;
        if (cursor == null || cursor.isBlank()) {
            members = memberRepository.findForAdminFirstPage(keyword, status, Limit.of(size + 1));
        } else {
            Cursor current = Cursor.decode(cursor);
            members = memberRepository.findForAdminNextPage(keyword, status, current.createdAt(), current.id(), Limit.of(size + 1));
        }

        String nextCursor = null;
        if (members.size() > size) {
            Member member = members.get(size - 1);
            nextCursor = new Cursor(member.getCreatedAt(), member.getId()).encode();
            members = members.subList(0, size);
        }

        return new CursorPage<>(members.stream().map(AdminMemberResult::from).toList(), nextCursor);
    }

    @Transactional
    public void ban(UUID memberId, String reason) {
        Member member = memberRepository.findById(memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        );

        member.ban(reason, clock.instant());
        tokenVersionService.bump(member);
        sessionStore.deleteAllOf(member.getId());
        outboxPublisher.publish(MemberTopics.MEMBER_BANNED, "MEMBER", memberId, new MemberBannedEvent(memberId, reason));
    }

    @Transactional
    public void unban(UUID memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        );

        member.unban();
    }
}
