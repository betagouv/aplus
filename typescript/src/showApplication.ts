import dialogPolyfill from "dialog-polyfill";
import "dialog-polyfill/dist/dialog-polyfill.css";

const reviewValidationButton = <HTMLButtonElement | null>document.getElementById('review-validation');
const customAnswerInput = <HTMLInputElement | null>document.getElementById('custom-answer');
const dialog = <HTMLDialogElement | null>document.querySelector('#dialog-terminate');
const reopenButton = <HTMLElement | null>document.getElementById('reopen-button');
const archiveButton1 = document.getElementById('archive-button-1');
const archiveButton2 = document.getElementById('archive-button-2');
const archiveButton3 = document.getElementById('archive-button-3');
const quickAnswer1Button = document.getElementById('option-1');
const quickAnswer2Button = document.getElementById('option-2');
const quickAnswer3Button = document.getElementById('option-3');
const quickAnswer4Button = document.getElementById('option-4');
const closeDialogTerminateButton = document.getElementById('close-dialog-terminate');
const closeDialogQuitButton = document.getElementById('close-dialog-quit');

const enableButtonAndDisableCustomAnswer = () => {
  reviewValidationButton.disabled = false;
  customAnswerInput.value = "";
  customAnswerInput.disabled = true;
  customAnswerInput.style.background = 'lightgrey';
}

const disableButtonAndEnableCustomAnswer = () => {
  reviewValidationButton.disabled = true;
  customAnswerInput.disabled = false;
  customAnswerInput.style.background = 'white';
}

const enableYes = () => {
  const button = <HTMLButtonElement | null>document.getElementById("close-dialog-terminate");
  button.disabled = false;
}

const closeDialog = () => dialog.close();

const showDialog = () => {
  Array.from(document.querySelectorAll("#dialog-terminate input")).forEach((input: HTMLInputElement) => input.checked = false)
  dialog.showModal();
}

const confirmTerminate = () => {
  const targetUrl = closeDialogTerminateButton?.dataset.targetUrl;
  const checked = <HTMLInputElement | null>document.querySelector('input[name="usefulness"]:checked');
  const usefulness = checked.value;
  document.location.href = targetUrl + '?usefulness=' + usefulness;
}

const reopen = () => document.location.href = reopenButton?.dataset.targetUrl;

const enableFeedbackOnChangeForComponentId = (id: string) => document.getElementById(id)?.addEventListener('change', enableYes);

quickAnswer1Button?.addEventListener('click', enableButtonAndDisableCustomAnswer);
quickAnswer2Button?.addEventListener('click', enableButtonAndDisableCustomAnswer);
quickAnswer3Button?.addEventListener('click', enableButtonAndDisableCustomAnswer);
quickAnswer4Button?.addEventListener('click', disableButtonAndEnableCustomAnswer);
reopenButton?.addEventListener('click', () => reopen());
closeDialogTerminateButton?.addEventListener('click', confirmTerminate);
closeDialogQuitButton?.addEventListener('click', closeDialog);
archiveButton1?.addEventListener('click', showDialog);
archiveButton2?.addEventListener('click', showDialog);
archiveButton3?.addEventListener('click', showDialog);

enableFeedbackOnChangeForComponentId('no');
enableFeedbackOnChangeForComponentId('yes');
enableFeedbackOnChangeForComponentId('neutral');

if (dialog && !dialog.showModal) {
  dialogPolyfill.registerDialog(dialog);
}
