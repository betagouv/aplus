import SlimSelect from "slim-select";


const slimSelectClass = "use-slimselect";



setupSlimSelect();



//
// Transform <select> with SlimSelect
//
function setupSlimSelect() {
  Array.from(document.querySelectorAll("." + slimSelectClass)).forEach(function(select) {
    new SlimSelect({ select: select, selectByGroup: true, closeOnSelect: false });
  });
}
