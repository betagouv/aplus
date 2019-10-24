
function confirmUserDeletion() {
    document.getElementById('user-deletion-modal').classList.add('is-visible');
}

function closeModal() {
    document
        .getElementById('modal-close-button')
            .parentElement
                .parentElement
                    .parentElement.classList.remove('is-visible');
}

function confirmUnusedUserDeletionModal(uuid) {
    var xhr = new XMLHttpRequest();
    xhr.open('POST', '/users/'+uuid, false);
    xhr.onreadystatechange = function() {
        if (this.readyState === XMLHttpRequest.DONE && this.status === 303) {
            window.location = xhr.getResponseHeader('Location');
        }
    }
    xhr.send();
}