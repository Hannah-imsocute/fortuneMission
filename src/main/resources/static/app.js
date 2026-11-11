const form = document.querySelector('#fortune-form');
const generateButton = document.querySelector('#generate-button');
const loadingMessage = document.querySelector('#loading-message');
const errorMessage = document.querySelector('#error-message');
const resultCard = document.querySelector('.result-card');
const exportStatus = document.querySelector('#export-status');
let currentResult;

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  generateButton.disabled = true;
  generateButton.firstElementChild.textContent = '미션을 만들고 있어요';
  loadingMessage.hidden = false;
  errorMessage.hidden = true;
  resultCard.setAttribute('aria-busy', 'true');

  try {
    const response = await fetch('/api/fortunes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(Object.fromEntries(new FormData(form))),
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.message || '운세를 생성하지 못했습니다. 다시 시도해주세요.');
    renderResult(result);
  } catch (error) {
    errorMessage.textContent = error instanceof TypeError
      ? '서버에 연결하지 못했습니다. 연결 상태를 확인한 후 다시 시도해주세요.'
      : error.message;
    errorMessage.hidden = false;
  } finally {
    generateButton.disabled = false;
    generateButton.firstElementChild.textContent = '오늘의 미션 받기';
    loadingMessage.hidden = true;
    resultCard.setAttribute('aria-busy', 'false');
  }
});

function renderResult(result) {
  currentResult = result;
  document.querySelector('#result-date').textContent = `${result.date} · 나를 위한 오늘의 기록`;
  document.querySelector('#summary').textContent = result.summary;
  exportStatus.textContent = '';

  const pillars = document.querySelector('#pillars');
  pillars.replaceChildren();
  for (const [label, key] of [['년주', 'yearPillar'], ['월주', 'monthPillar'], ['일주', 'dayPillar'], ['시주', 'timePillar']]) {
    const item = document.createElement('div');
    const name = document.createElement('span');
    const value = document.createElement('strong');
    name.textContent = label;
    value.textContent = result.saju[key];
    item.append(name, value);
    pillars.append(item);
  }

  const elements = document.querySelector('#elements');
  elements.replaceChildren();
  for (const [name, count] of Object.entries(result.saju.elements)) {
    const item = document.createElement('span');
    item.textContent = `${name} ${count}`;
    elements.append(item);
  }

  const quests = document.querySelector('#quests');
  quests.replaceChildren();
  result.quests.forEach((quest) => {
    const item = document.createElement('li');
    const label = document.createElement('label');
    const checkbox = document.createElement('input');
    const text = document.createElement('span');
    checkbox.type = 'checkbox';
    text.textContent = quest;
    checkbox.addEventListener('change', () => {
      document.querySelector('#quest-count').textContent = `${quests.querySelectorAll('input:checked').length} / 3 완료`;
    });
    label.append(checkbox, text);
    item.append(label);
    quests.append(item);
  });
  document.querySelector('#quest-count').textContent = '0 / 3 완료';
  document.querySelector('#empty-result').hidden = true;
  document.querySelector('#fortune-result').hidden = false;
}

function resultText() {
  const { date, saju, summary, quests } = currentResult;
  return [
    `포춘미션 · ${date}`,
    '',
    `사주: 년주 ${saju.yearPillar} / 월주 ${saju.monthPillar} / 일주 ${saju.dayPillar} / 시주 ${saju.timePillar}`,
    `오행: ${Object.entries(saju.elements).map(([name, count]) => `${name} ${count}`).join(' / ')}`,
    '',
    '[오늘의 운세]',
    summary,
    '',
    '[오늘의 미션]',
    ...quests.map((quest, index) => `${index + 1}. ${quest}`),
  ].join('\n');
}

document.querySelector('#copy-button').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(resultText());
    exportStatus.textContent = '답변을 복사했습니다.';
  } catch {
    exportStatus.textContent = '이 브라우저에서는 복사할 수 없습니다. 텍스트 다운로드를 이용해주세요.';
  }
});

document.querySelector('#download-button').addEventListener('click', () => {
  const url = URL.createObjectURL(new Blob([resultText()], { type: 'text/plain;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `fortune-mission-${currentResult.date}.txt`;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  exportStatus.textContent = '답변을 텍스트 파일로 내려받았습니다.';
});
