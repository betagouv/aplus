import dialogPolyfill from "dialog-polyfill";
import "dialog-polyfill/dist/dialog-polyfill.css";

const customAnswerInput = <HTMLInputElement | null>document.getElementById('custom-answer');
const dialog = <HTMLDialogElement | null>document.querySelector('#dialog-terminate');
const archiveButton1 = document.getElementById('archive-button-1');
const archiveButton2 = document.getElementById('archive-button-2');
const archiveButton3 = document.getElementById('archive-button-3');
const quickAnswer1Button = document.getElementById('option-1');
const quickAnswer3Button = document.getElementById('option-3');
const quickAnswer4Button = document.getElementById('option-4');
const applicationProcessedCheckbox = document.getElementById('application-processed-checkbox');
const closeDialogQuitButton = document.getElementById('close-dialog-quit');



if (dialog && !dialog.showModal) {
  dialogPolyfill.registerDialog(dialog);
}

const closeDialog = () => dialog?.close();

const showDialog = () => {
  Array.from(document.querySelectorAll<HTMLInputElement>("#dialog-terminate input"))
    .forEach((input: HTMLInputElement) => input.checked = false);
  dialog?.showModal();
}

const enableButtonAndDisableCustomAnswer = () => {
  if (applicationProcessedCheckbox) {
    applicationProcessedCheckbox.classList.add("hidden");
  }
  if (customAnswerInput) {
    customAnswerInput.value = "";
    customAnswerInput.disabled = true;
    customAnswerInput.style.background = 'lightgrey';
    customAnswerInput.classList.add("hidden");
  }
}

const onCustomAnswerClick = () => {
  if (applicationProcessedCheckbox) {
    applicationProcessedCheckbox.classList.remove("hidden");
  }
  if (customAnswerInput) {
    customAnswerInput.disabled = false;
    customAnswerInput.style.background = 'white';
    customAnswerInput.classList.remove("hidden");
  }
}

quickAnswer1Button?.addEventListener('click', enableButtonAndDisableCustomAnswer);
quickAnswer3Button?.addEventListener('click', enableButtonAndDisableCustomAnswer);
quickAnswer4Button?.addEventListener('click', onCustomAnswerClick);
closeDialogQuitButton?.addEventListener('click', closeDialog);
archiveButton1?.addEventListener('click', showDialog);
archiveButton2?.addEventListener('click', showDialog);
archiveButton3?.addEventListener('click', showDialog);



const enableYes = () => {
  const button = <HTMLButtonElement | null>document.getElementById("close-dialog-terminate");
  if (button != null) {
    button.disabled = false;
  }
}

const enableFeedbackOnChangeForComponentId = (id: string) => document.getElementById(id)?.addEventListener('change', enableYes);

enableFeedbackOnChangeForComponentId('no');
enableFeedbackOnChangeForComponentId('yes');
enableFeedbackOnChangeForComponentId('neutral');
