import SlimSelect from "slim-select";


const slimSelectClass = "use-slimselect";

//
// Transform <select> with SlimSelect
//
document.addEventListener("DOMContentLoaded", function() {
  Array.from(document.querySelectorAll("." + slimSelectClass)).forEach(function(select) {
    new SlimSelect({ select, selectByGroup: true, closeOnSelect: false });
  });
});
