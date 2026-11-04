package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.InquiryAnswer;
import com.myroutine.member.domain.InquiryAnswerRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaInquiryAnswerRepository extends JpaRepository<InquiryAnswer, UUID>, InquiryAnswerRepository {
}
