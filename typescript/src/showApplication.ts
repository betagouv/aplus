import type { HTMLDialogElement } from "./dialog";
import dialogPolyfill from "dialog-polyfill";
import "dialog-polyfill/dist/dialog-polyfill.css";

const reviewValidationButton = <HTMLButtonElement | null>document.getElementById('review-validation');
const customAnswerInput = <HTMLInputElement | null>document.getElementById('custom-answer');
const nonInstructorAnswerInput = <HTMLInputElement | null>document.getElementById('non-instructor-answer');
const dialog = <HTMLDialogElement | null>document.querySelector('#dialog-terminate');
const archiveButton1 = document.getElementById('archive-button-1');
const archiveButton2 = document.getElementById('archive-button-2');
const archiveButton3 = document.getElementById('archive-button-3');
const quickAnswer1Button = document.getElementById('option-1');
const quickAnswer2Button = document.getElementById('option-2');
const quickAnswer3Button = document.getElementById('option-3');
const quickAnswer4Button = document.getElementById('option-4');
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
  if (reviewValidationButton) {
    reviewValidationButton.disabled = false;
  }
  if (customAnswerInput) {
    customAnswerInput.value = "";
    customAnswerInput.disabled = true;
    customAnswerInput.style.background = 'lightgrey';
  }
}

const disableButtonAndEnableCustomAnswer = () => {
  if (reviewValidationButton) {
    reviewValidationButton.disabled = true;
  }
  if (customAnswerInput) {
    customAnswerInput.disabled = false;
    customAnswerInput.style.background = 'white';
  }
}

quickAnswer1Button?.addEventListener('click', enableButtonAndDisableCustomAnswer);
quickAnswer2Button?.addEventListener('click', enableButtonAndDisableCustomAnswer);
quickAnswer3Button?.addEventListener('click', enableButtonAndDisableCustomAnswer);
quickAnswer4Button?.addEventListener('click', disableButtonAndEnableCustomAnswer);
closeDialogQuitButton?.addEventListener('click', closeDialog);
archiveButton1?.addEventListener('click', showDialog);
archiveButton2?.addEventListener('click', showDialog);
archiveButton3?.addEventListener('click', showDialog);

if (customAnswerInput && reviewValidationButton) {
  customAnswerInput.addEventListener("keyup", () => {
    reviewValidationButton.disabled = customAnswerInput.value === '';
  });
}

if (nonInstructorAnswerInput && reviewValidationButton) {
  nonInstructorAnswerInput.addEventListener("keyup", () => {
    reviewValidationButton.disabled = nonInstructorAnswerInput.value === '';
  });
}



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
