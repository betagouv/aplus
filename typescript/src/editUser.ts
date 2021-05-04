import { addDialogButtonsClickListeners } from "./dialog";

const deleteUserButtonId = "delete-user-button";
const deleteUserModalId = "dialog-delete-user";
const deleteUserModalConfirmButtonId = "delete-user-modal-confirm-button";
const deleteUserModalQuitButtonId = "delete-user-modal-quit-button";



setupDeleteUserModal();



function setupDeleteUserModal() {

  addDialogButtonsClickListeners(
    deleteUserModalId,
    deleteUserButtonId,
    deleteUserModalQuitButtonId
  );

  const confirmButton =
    <HTMLButtonElement | null>document.getElementById(deleteUserModalConfirmButtonId);

  if (confirmButton != null) {
    confirmButton.addEventListener("click", () => {
      const userId = confirmButton.dataset["userId"];
      if (userId != null) {
        const url = jsRoutes.controllers.UserController.deleteUnusedUserById(userId).url;
        window.location = url;
      }
    });
  }

}
