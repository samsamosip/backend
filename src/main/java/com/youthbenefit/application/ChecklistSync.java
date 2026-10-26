package com.youthbenefit.application;

import com.youthbenefit.policy.Policy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공고 제출 서류가 바뀌면 그 공고를 담은 준비함 체크리스트에 반영한다({@link Application#syncDocuments}). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChecklistSync {

	private final ApplicationRepository repository;

	/** @return 체크리스트가 바뀐 준비함 수 */
	@Transactional
	public int refresh(Policy policy) {
		List<DocumentListParser.Document> documents = DocumentListParser.documents(policy);
		int changed = 0;
		for (Application application : repository.findAllByPolicyId(policy.getId())) {
			if (application.syncDocuments(documents, true)) {
				changed++;
			}
		}
		if (changed > 0) {
			log.info("공고 {} 서류 변경: 체크리스트 {}개 반영", policy.getId(), changed);
		}
		return changed;
	}

}
