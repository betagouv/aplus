import { addDialogButtonsClickListenersByIds } from "./dialog";

const dialogDeleteGroupId = "dialog-delete-group";
const dialogDeleteGroupButtonShowId = "dialog-delete-group-show";
const dialogDeleteGroupButtonCancelId = "dialog-delete-group-cancel";



function setupDeleteGroupModal() {

  addDialogButtonsClickListenersByIds(
    dialogDeleteGroupId,
    dialogDeleteGroupButtonShowId,
    dialogDeleteGroupButtonCancelId
  );

  addDialogButtonsClickListenersByIds(
    "dialog-remove-all-users-from-group",
    "dialog-remove-all-users-from-group-show",
    "dialog-remove-all-users-from-group-cancel"
  );

}



setupDeleteGroupModal();
