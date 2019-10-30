Group = {
};

window.document.addEventListener("DOMContentLoaded", function(event) {
    var dialog = document.getElementById("dialog-delete-group");

    if (dialog && !dialog.showModal) {
        dialogPolyfill.registerDialog(dialog);
    }

    Group.closeGroupRemovalDialog = function() {
        dialog.close();
    }

    Group.showGroupRemovalDialog = function() {
        dialog.showModal();
    }

    Group.confirmRemovalDialog = function(uuid) {
        var url = jsRoutes.controllers.GroupController.deleteUnusedGroupById(uuid).url
        window.location = url;
    }
}, false);
