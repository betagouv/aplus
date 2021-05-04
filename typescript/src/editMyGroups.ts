import dialogPolyfill from "dialog-polyfill";
import "dialog-polyfill/dist/dialog-polyfill.css";

const removeUserFromGroupButtonClass = "remove-user-from-group-button";
const removeUserFromGroupDialogId = (groupId: string, userId: string) =>
  `remove-user-from-group-dialog-${ groupId }-${ userId }`
const closeModalClass = "close-modal"



setupRemoveUserFromGroupModal();




function setupRemoveUserFromGroupModal() {

  Array.from(document.querySelectorAll<HTMLElement>("." + removeUserFromGroupButtonClass)).forEach(button => {
    const userId = button.dataset["userId"];
    const groupId = button.dataset["groupId"];
    if (userId != null && groupId != null) {
      const dialogId = removeUserFromGroupDialogId(groupId, userId);
      const dialog = <HTMLDialogElement | null>document.getElementById(dialogId);

      if (dialog != null) {
        if (dialog && !dialog.showModal) {
          dialogPolyfill.registerDialog(dialog);
        }
        button.addEventListener("click", () => {
          dialog.showModal();
        })
        const closeButton = <HTMLButtonElement | null>dialog.querySelector("." + closeModalClass);
        if (closeButton != null) {
          closeButton.addEventListener("click", () => dialog.close());
        }
      }

    }
  })

}
