import { addDialogButtonsClickListenersByIds } from "./dialog";

const dialogDeleteGroupId = "dialog-delete-group";
const dialogDeleteGroupButtonShowId = "dialog-delete-group-show";
const dialogDeleteGroupButtonCancelId = "dialog-delete-group-cancel";
const dialogDeleteGroupButtonConfirmId = "dialog-delete-group-confirm";



setupDeleteGroupModal();



function setupDeleteGroupModal() {

  addDialogButtonsClickListenersByIds(
    dialogDeleteGroupId,
    dialogDeleteGroupButtonShowId,
    dialogDeleteGroupButtonCancelId
  );

  const confirmButton =
    <HTMLButtonElement | null>document.getElementById(dialogDeleteGroupButtonConfirmId);

  if (confirmButton != null) {
    confirmButton.addEventListener('click', () => {
      const groupId = confirmButton.dataset["uuid"];
      if (groupId != null) {
        const url = jsRoutes.controllers.GroupController.deleteUnusedGroupById(groupId).url;
        window.location = url;
      }
    });
  }

}
