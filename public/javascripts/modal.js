
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

function confirmUnusedUserDeletionModal(uuid, tokenName, tokenValue) {
    window.location = '/user/delete/unused/'+uuid;
}