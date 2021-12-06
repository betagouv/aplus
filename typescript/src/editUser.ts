import { addDialogButtonsClickListenersByIds } from "./dialog";

const deleteUserButtonId = "delete-user-button";
const deleteUserModalId = "dialog-delete-user";
const deleteUserModalQuitButtonId = "delete-user-modal-quit-button";



function setupDeleteUserModal() {

  addDialogButtonsClickListenersByIds(
    deleteUserModalId,
    deleteUserButtonId,
    deleteUserModalQuitButtonId
  );

}



setupDeleteUserModal();
