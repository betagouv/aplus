const extract = 9;

const searchInput = <HTMLInputElement | null>document.getElementById("search-input");
const clearSearch = <HTMLButtonElement | null>document.getElementById("clear-search");

if (searchInput) {
  const onSearch = () => {
    const searchTerm = searchInput.value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, "");
    if (searchTerm.length > 2) {
      document.querySelectorAll("tfoot").forEach((row) => { row.classList.remove("invisible"); });
      document.querySelectorAll(".searchable-row").forEach((row) => {
        const searchData = row.getAttribute("data-search") || '';
        const searchResult = searchData.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, "").indexOf(searchTerm);
        const searchCell = row.querySelector(".search-cell");
        if (searchCell) {
          if (searchResult > -1) {
            row.classList.remove("invisible");
            const minIndex = Math.max(searchResult - extract, 0);
            const maxIndex = Math.min(searchResult + searchTerm.length + extract, searchData.length);
            searchCell.innerHTML = searchData.substring(minIndex, searchResult) +
              '<span style="font-weight: bold; background-color: #FFFF00;">' +
              searchData.substring(searchResult, searchResult + searchTerm.length) +
              "</span>" + searchData.substring(searchResult + searchTerm.length, maxIndex);
          } else {
            row.classList.add("invisible");
            searchCell.innerHTML = "";
          }
        }
      });
    } else {
      document.querySelectorAll(".searchable-row").forEach((row) => { row.classList.remove("invisible"); });
      document.querySelectorAll(".search-cell").forEach((cell) => { cell.innerHTML = ""; });
      document.querySelectorAll("tfoot").forEach((row) => { row.classList.add("invisible"); });
    }
  };

  searchInput.addEventListener('input', onSearch);
  searchInput.addEventListener('search', onSearch);
  if (clearSearch) {
    clearSearch.addEventListener('click', () => {
      searchInput.value = "";
      onSearch();
    });
  }

}
