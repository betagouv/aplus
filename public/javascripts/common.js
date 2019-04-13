function sendLoginMail(sender, email) {
    var request = new XMLHttpRequest();
    var parent = sender.parentNode;
    parent.innerHTML = "<span style='font-weight: bold;'>En cours</span> <img src='@routes.Assets.versioned("images/spin.gif")'>";
    request.open('POST', '@routes.LoginController.login()', true);
    request.setRequestHeader( 'Content-Type', 'application/x-www-form-urlencoded' );
    request.onload = function() {
        if (request.status === 200) {
            parent.innerHTML = "<span style='color: green; font-weight: bold;'>Ok !</span>";
        } else {
            parent.innerHTML = "<span style='color: red; font-weight: bold;'>Erreur !</span>";
        }
    };
    request.onerror = function() {
        parent.innerHTML = "<span style='color: red; font-weight: bold;'>Erreur !</span>";
    };
    request.send("email="+email);
}