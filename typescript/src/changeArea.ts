//
// Area Change Select
//

const changeAreaSelectId = 'changeAreaSelect';

const select = <HTMLSelectElement | null>document.getElementById(changeAreaSelectId);

if (select) {
  const currentArea = select.dataset["currentArea"];
  const redirectUrlPrefix = select.dataset["redirectUrlPrefix"];
  select.addEventListener('change', () => {
    if (redirectUrlPrefix) {
      const selectedArea = select.value;
      if (selectedArea !== currentArea) {
        document.location.href = redirectUrlPrefix + selectedArea;
      }
    } else {

      const selectedOption = select.options[select.selectedIndex];
      const redirectUrl = selectedOption.dataset["redirectUrl"];
      if (redirectUrl) {
        document.location.href = redirectUrl;
      }
    }
  });
}
