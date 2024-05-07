package com.example.backend.service;

import com.example.backend.dto.sign.ApproveDto;
import com.example.backend.dto.template.TemplateDto;
import com.example.backend.entity.maria.Document;
import com.example.backend.entity.maria.Ref;
import com.example.backend.entity.maria.TaskProgress;
import com.example.backend.entity.maria.User;
import com.example.backend.entity.maria.enumData.DocState;
import com.example.backend.entity.mongo.Template;
import com.example.backend.entity.mongo.TypeData;
import com.example.backend.repository.DocumentRepository;
import com.example.backend.repository.RefRepository;
import com.example.backend.repository.TaskProgressRepository;
import com.example.backend.repository.TemplateRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;


@Service
@RequiredArgsConstructor
@Log4j2
public class SignService {

    private final DocumentRepository documentRepository;
    private final TemplateRepository templateRepository;
    private final TaskProgressRepository taskProgressRepository;
    private final RefRepository refRepository;

    // MongoDB template ID값 반환

    /**
     * 작성한 휴가, 출장, 보고서, 경비 문서를 MongoDB(문서 저장), Document(문서 메타 정보) \n
     * task_progress(진행 상황 저장소)에 저장
     * 
     * @param templateDto   휴가, 출장, 보고서, 경비 Template 입력
     * @return String   MongoDB에 저장된 Template ObjectId값 반환
     */
    public String saveTemplate(TemplateDto templateDto) {

        LocalDate createDate = LocalDate.now();

        // mongodb 템플릿 저장
        Template<? extends TypeData> template = templateDto.toTemplateEntity();
        templateRepository.save(template);

        log.info(template);

        // Document Table 템플릿 메타 정보 저장
        Document document = templateDto.toDocumentEntity(template.getId(), createDate);
        documentRepository.save(document);

        // TaskProgress Table 결재 진행 상황 저장
        TaskProgress taskProgressApprover = TaskProgress.builder()
                .documentId(template.getId())
                .state(DocState.PROCESS_1)
                .ref_user_id(templateDto.getApproverList().get(0))
                .build();

        // 작성자 확인용 결재 진행 상황 저장
        TaskProgress taskProgressWriter = TaskProgress.builder()
                .documentId(template.getId())
                .state(DocState.PROCESS_1)
                .ref_user_id(templateDto.getWriter())
                .build();

        taskProgressRepository.save(taskProgressApprover);
        taskProgressRepository.save(taskProgressWriter);

        return template.getId();
    }

    /**
     * 결재 문서 승인 기능
     * @param user 결재자 정보
     * @param approveDto 결재 정보: 문서 id, 결재 여부(True, False)
     */
    public void approve(User user, ApproveDto approveDto) {
        
        // 문서 가져오기
        Template template = templateRepository.findById(approveDto.getDocumentId()).orElseThrow(() -> new IllegalArgumentException("template is not exist"));

        List<Long> approveList = template.getApproverList();
        List<Boolean> approveCheckList = template.getApproverCheckList();

        int index = approveCheckList.size();

        // 결재 체크 리스트를 확인하여 결재 권한이 있는지 확인
        if(user.getUserId() != approveList.get(index))
            throw new IllegalArgumentException("현재 결재 승인 권한이 존재하지 않습니다.");

        // 결재 거부일 경우
        if(approveDto.getApprovalState() == false)
            documentDeny(template, approveCheckList);

        // 결재 승인일 경우
        if(approveDto.getApprovalState() == true)
            documentApprove(template, approveCheckList);

    }


    /**
     * 결재 거부
     * @param template
     * @param approveCheckList
     */
    @Transactional
    public void documentDeny(Template template, List<Boolean> approveCheckList) {
        approveCheckList.add(false);
        template.updateCheckList(approveCheckList);

        // approveCheckList template 수정
        templateRepository.save(template);

        // document 메타 정보 상태 deny로 수정
        Document document = documentRepository.findById(template.getId()).orElseThrow(() -> new IllegalArgumentException("문서 정보가 없습니다."));
        document.updateState(DocState.DENY);

        // task_progress에서 삭제
        taskProgressRepository.deleteAllByDocumentId(template.getId());

        // Ref table에서 작성자 참조 상태 deny 설정 - 다른 참조자 결재자는 설정 안함.
        Ref ref = Ref.builder()
                .documentId(template.getId())
                .state(DocState.DENY)
                .refUserId(template.getWriter())
                .build();

        refRepository.save(ref);

    }

    /**
     * 결재 승인
     * @param template
     * @param approveCheckList
     */
    @Transactional
    public void documentApprove(Template template, List<Boolean> approveCheckList) {
        approveCheckList.add(true);
        template.updateCheckList(approveCheckList);

        // approveCheckList template 수정
        templateRepository.save(template);

        // 결재 완료
        if (template.getApproverList().size() == approveCheckList.size()) {
            // document 메타 정보 상태 Complete로 수정
            Document document = documentRepository.findById(template.getId()).orElseThrow(() -> new IllegalArgumentException("문서 정보가 없습니다."));
            document.updateState(DocState.COMPLETE);

            // task_progress 테이블에서 삭제
            taskProgressRepository.deleteAllByDocumentId(template.getId());

            // 참조자에게 참조 지정
            List<Long> refList = template.getRefList();
            refList.stream().forEach((ref_id) -> {
                Ref ref = Ref.builder()
                        .documentId(template.getId())
                        .state(DocState.COMPLETE)
                        .refUserId(ref_id)
                        .build();
                refRepository.save(ref);
            });

            // 작성자에게 참조 지정
            Ref ref = Ref.builder()
                    .documentId(template.getId())
                    .state(DocState.COMPLETE)
                    .refUserId(template.getWriter())
                    .build();

            refRepository.save(ref);
        }
        // 결재 진행 중
        else {
            
            // document 메타 정보 상태 수정
            Document document = documentRepository.findById(template.getId()).orElseThrow(() -> new IllegalArgumentException("문서 정보가 없습니다."));
            DocState docState = changeState(template.getApproverCheckList().size());
            
            document.updateState(docState);

            // task_progress 테이블에서 삭제
            taskProgressRepository.deleteAllByDocumentId(template.getId());

            // task_progress 결재자, 작성자 내용 저장
            TaskProgress taskProgressWriter = TaskProgress.builder()
                    .documentId(template.getId())
                    .state(docState)
                    .ref_user_id(template.getWriter())
                    .build();

            TaskProgress taskProgressApproval = TaskProgress.builder()
                    .documentId(template.getId())
                    .state(docState)
                    .ref_user_id((Long) template.getApproverList().get(template.getApproverCheckList().size()))
                    .build();

            taskProgressRepository.save(taskProgressWriter);
            taskProgressRepository.save(taskProgressApproval);

        }
    }

    /**
     * 결재 상태 반환
     * @param check 현재 결재자 수
     * @return DocState 문서 현재 상태
     */
    public DocState changeState(int check) {
        if(check == 1)
            return DocState.PROCESS_2;
        if(check == 2)
            return DocState.PROCESS_3;
        
        throw new IllegalArgumentException("결재자 오류");
    }


    /**
     * 템플릿 임시 저장
     * @param user
     * @param templateDto
     */
    public void temporaryStorage(User user,TemplateDto templateDto) {
        

        // mongodb 템플릿 저장
        Template<? extends TypeData> template = templateDto.toTemplateEntity();
        templateRepository.save(template);

        // Document Table 템플릿 메타 정보는 필요하지 않으므로 저장하지 않음
        
        // TaskProgress에 작성 상태 저장
        TaskProgress taskProgressApprover = TaskProgress.builder()
                .documentId(template.getId())
                .state(DocState.TEMPORARY)
                .ref_user_id(templateDto.getWriter())
                .build();
    }

    /**
     * 임시 저장 템플릿 가져오기 + 삭제
     * @param user
     * @return 저장된 임시저장 템플릿 반환
     */
//    public Template getTemporaryStorage(User user) {
//
//        TaskProgress taskProgress = taskProgressRepository.findByRef_user_idAndState(user.getUserId(), DocState.TEMPORARY)
//                .orElseThrow(() -> new IllegalArgumentException("임시 저장 파일이 없습니다."));
//
//        // 임시파일 저장 확인
//        Template template = templateRepository.findById(taskProgress.getDocumentId()).orElseThrow(() -> new IllegalArgumentException("임시저장 템플릿이 없음"));
//
//        // 임시파일 삭제
//        templateRepository.delete(template);
//        taskProgressRepository.deleteByDocumentId(taskProgress.getDocumentId());
//
//
//        return template;
//
//    }

}