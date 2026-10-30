package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.member.domain.*;
import com.myroutine.member.infrastructure.OAuthClients;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private final OAuthClients clients;
    private final SignupTokenStore signupTokenStore;
    private final SignupVerificationStore signupVerificationStore;
    private final SocialAccountRepository socialAccountRepository;
    private final MemberRepository memberRepository;
    private final TokenIssuer tokenIssuer;

    public OAuthLoginResult login(OAuthProvider provider, String code, String state) {
        OAuthUserInfo userInfo = clients.get(provider).fetchUser(code, state);

        Optional<SocialAccount> socialAccount = socialAccountRepository.findByProviderAndProviderUserId(provider, userInfo.providerUserId());

        if (socialAccount.isPresent()) {
            Member member = memberRepository.findById(socialAccount.get().getMemberId()).orElseThrow();
            member.verifyCanLogin();
            TokenResult token = tokenIssuer.issueNewSession(member);
            return new OAuthLoginResult("LOGGED_IN", token, null, null);
        }

        String token = signupTokenStore.issue(provider, userInfo);

        return new OAuthLoginResult("SIGNUP_REQUIRED", null, token, userInfo.email());
    }

    @Transactional
    public TokenResult signup(SocialSignupCommand command) {
        Optional<PendingSocialSignup> pending = signupTokenStore.find(command.signupToken());
        if (pending.isEmpty()) {
            throw new BusinessException(MemberErrorCode.SIGNUP_TOKEN_INVALID);
        }

        PendingSocialSignup pendingSignup = pending.get();

        String email = pendingSignup.email() == null ? command.email() : pendingSignup.email();
        if (email == null || email.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        email = Member.normalizeEmail(email);

        Optional<String> verifiedEmail = signupVerificationStore.findEmail(command.verificationToken());
        if (verifiedEmail.isEmpty() || !email.equals(verifiedEmail.get())) {
            throw new BusinessException(MemberErrorCode.VERIFICATION_TOKEN_INVALID);
        }

        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
        }

        if (memberRepository.existsByNickname(command.nickname())) {
            throw new BusinessException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }

        Member member = memberRepository.save(Member.signUpWithSocial(email, command.nickname(), command.name()));
        SocialAccount socialAccount = SocialAccount.link(member.getId(), pendingSignup.provider(), pendingSignup.providerUserId(), pendingSignup.email());
        socialAccountRepository.save(socialAccount);

        signupTokenStore.delete(command.signupToken());
        signupVerificationStore.delete(command.verificationToken());

        return tokenIssuer.issueNewSession(member);
    }

    public void link(UUID memberId, OAuthProvider provider, String code, String state) {
        OAuthUserInfo userInfo = clients.get(provider).fetchUser(code, state);
        if (socialAccountRepository.existsByMemberIdAndProvider(memberId, provider) || socialAccountRepository.existsByProviderAndProviderUserId(provider, userInfo.providerUserId())) {
            throw new BusinessException(MemberErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
        }
        SocialAccount socialAccount = SocialAccount.link(memberId, provider, userInfo.providerUserId(), userInfo.email());
        socialAccountRepository.save(socialAccount);
    }
}
