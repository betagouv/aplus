import dialogPolyfill from "dialog-polyfill";
import "dialog-polyfill/dist/dialog-polyfill.css";

// Not in dom.d.ts anymore
// https://github.com/microsoft/TypeScript-DOM-lib-generator/issues/1029
export interface HTMLDialogElement extends HTMLElement {
  close: () => void
  showModal: () => void
}


export function addDialogButtonsClickListeners(
  dialog: HTMLDialogElement | null,
  showButton: HTMLElement | null,
  closeButton: HTMLElement | null,
) {
  if (showButton != null && dialog != null && closeButton != null) {
    if (!dialog.showModal) {
      dialogPolyfill.registerDialog(dialog);
    }

    showButton.addEventListener("click", () => dialog.showModal());
    closeButton.addEventListener("click", () => dialog.close());
  }
}


export function addDialogButtonsClickListenersByIds(
  dialogId: string,
  showModalButtonId: string,
  closeModalButtonId: string,
) {
  const showButton = <HTMLButtonElement | null>document.getElementById(showModalButtonId);
  const dialog = <HTMLDialogElement | null>document.getElementById(dialogId);
  const closeButton = <HTMLButtonElement | null>document.getElementById(closeModalButtonId);
  addDialogButtonsClickListeners(dialog, showButton, closeButton);
}
