import { addDialogButtonsClickListeners } from "./dialog";

const removeUserFromGroupButtonClass = "remove-user-from-group-button";
const removeUserFromGroupDialogId = (groupId: string, userId: string) =>
  `remove-user-from-group-dialog-${ groupId }-${ userId }`
const closeModalClass = "close-modal"



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



setupRemoveUserFromGroupModal();



function setupDisabledUsersToggle() {
  Array.from(document.querySelectorAll<HTMLElement>(".group-container"))
    .forEach((container) => {

      const toggle = container.querySelector<HTMLElement>(".disabled-users-toggle");
      if (toggle == null) {
        return;
      }

      const disabledUsersRows = Array.from(container.querySelectorAll<HTMLElement>(".user-is-disabled"));
      if (disabledUsersRows[0] == null) {
        return;
      }
      const firstDisabled: HTMLElement = disabledUsersRows[0];

      const toggleHiddenRows = () => {
        const isHidden = firstDisabled.classList.contains("hidden");
        if (isHidden) {
          disabledUsersRows.forEach((e) => e.classList.remove("hidden"));
          toggle.textContent = "Masquer les membres désactivés";
        } else {
          disabledUsersRows.forEach((e) => e.classList.add("hidden"));
          toggle.textContent = "Voir les membres désactivés";
        }
      };

      toggle.addEventListener('click', toggleHiddenRows);
      toggle.addEventListener('keydown', function(e) {
        // '13' is the key code for Enter
        if (e.keyCode === 13) {
          toggleHiddenRows()
        }
      });
    });
}

setupDisabledUsersToggle();
