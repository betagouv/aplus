const showClosedApplicationsToggleId = "show-closed-toggle";

const button = document.getElementById(showClosedApplicationsToggleId);

if (button) {
  button.addEventListener('click', () => {
    document.getElementById("show-closed-applications").classList.add("invisible");
    document.getElementById("closed-applications").classList.remove("invisible");
  });
}
