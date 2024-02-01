const extract = 9;

const searchInput = <HTMLInputElement | null>document.getElementById("search-input");
const clearSearch = <HTMLButtonElement | null>document.getElementById("clear-search");
if (searchInput) {
  const onSearch = () => {
    const searchTerm = searchInput.value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, "");
    if (searchTerm.length > 2) {
      document.querySelectorAll(".aplus-application-link").forEach((row) => { row.classList.remove("invisible"); });
      document.querySelectorAll(".searchable-row").forEach((row) => {
        const hintContainer = row.closest(".aplus-application-link");
        if (!hintContainer) return;

        const searchData = row.getAttribute("data-search") || '';
        const searchResult = searchData.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, "").indexOf(searchTerm);
        if (searchResult > -1) {
          row.innerHTML = "";
          hintContainer.classList.remove("invisible");
          const minIndex = Math.max(searchResult - extract, 0);
          const maxIndex = Math.min(searchResult + searchTerm.length + extract, searchData.length);
          const container = document.createElement("div");
          const containerTitle = document.createElement("p");

          containerTitle.innerHTML = 'Résultats trouvés : '

          container.innerHTML = searchData.substring(minIndex, searchResult) +
            '<span style="font-weight: bold; background-color: #e3e3fd;">' +
            searchData.substring(searchResult, searchResult + searchTerm.length) +
            "</span>" + searchData.substring(searchResult + searchTerm.length, maxIndex);
          row.append(containerTitle);
          row.append(container);
        } else {
          hintContainer.classList.add("invisible");
          row.innerHTML = "";
        }
        
      });
    } else {
      document.querySelectorAll(".aplus-application-link").forEach((row) => { row.classList.remove("invisible"); });
      document.querySelectorAll(".searchable-row").forEach((row) => { row.innerHTML = ""; });
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
