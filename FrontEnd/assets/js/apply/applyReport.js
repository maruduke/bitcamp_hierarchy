import { getApproveList, getReferenceList, initAsnc } from './applyCommon.js';
import { applyService } from '../apis/applyAPI.js';

const element = {
    title: document.querySelector('#title'),
    detail: document.querySelector('#detail'),
    submit: document.querySelector('#submit'),
    tempStore: document.querySelector('#tempStore'),
};

const createTemplateData = () => {
    const approves = getApproveList();
    const references = getReferenceList();

    const template = {
        type: 'REPORT',
        refList: references,
        approverList: approves,
        title: title.value,
        detail: detail.value,
    };

    return template;
};

element.submit.addEventListener('click', () => {
    const template = createTemplate();

    applyService.createTemplate(template, () => {
        alert('결재 신청이 완료되었습니다.');
        location.href = 'http://localhost:3200/approve/main';
    });
});

element.tempStore.addEventListener('click', () => {
    const template = createTemplate();

    applyService.tempStoreTemplate(template, () => {
        alert('임시 저장 되었습니다.');
        location.href = 'http://localhost:3200/approve/main';
    });
});

initAsnc();
