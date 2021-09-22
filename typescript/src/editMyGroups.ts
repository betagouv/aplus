import type { HTMLDialogElement } from "./dialog";
import { addDialogButtonsClickListeners } from "./dialog";

const removeUserFromGroupButtonClass = "remove-user-from-group-button";
const removeUserFromGroupDialogId = (groupId: string, userId: string) =>
  `remove-user-from-group-dialog-${ groupId }-${ userId }`
const closeModalClass = "close-modal"



setupRemoveUserFromGroupModal();




function setupRemoveUserFromGroupModal() {

  Array.from(document.querySelectorAll<HTMLElement>("." + removeUserFromGroupButtonClass))
    .forEach(button => {
      const userId = button.dataset["userId"];
      const groupId = button.dataset["groupId"];
      if (userId != null && groupId != null) {
        const dialogId = removeUserFromGroupDialogId(groupId, userId);
        const dialog = <HTMLDialogElement | null>document.getElementById(dialogId);
        const closeBtn = dialog ? dialog.querySelector<HTMLElement>("." + closeModalClass) : null;
        addDialogButtonsClickListeners(
          dialog,
          button,
          closeBtn,
        );
      }
    })

}
