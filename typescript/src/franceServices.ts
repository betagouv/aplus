/* global jsRoutes */
import { CellEditEventCallback, CellEventCallback, ColumnDefinition, Formatter, Options, Tabulator, TabulatorFull } from 'tabulator-tables';
import 'tabulator-tables/dist/css/tabulator.css';

const tableId = 'tabulator-france-services-table';
const downloadBtnCsv = 'france-services-download-btn-csv';
const downloadBtnXlsx = 'france-services-download-btn-xlsx';
const alertsId = 'france-services-alerts';

const addTableId = 'tabulator-france-services-add-table';
const addLineBtnId = 'add-france-services-new-line';
const addCsvBtnId = 'add-france-services-csv';
const addDownloadCsvBtnId = 'add-france-services-download-csv';
const addDedupBtnId = 'add-france-services-dedup';
const addUploadBtnId = 'add-france-services-upload';
const addAlertsId = 'france-services-add-alerts';



interface ApiError {
  message: string | null;
}

interface Group {
  id: string;
  name: string;
}

interface NewMatricule {
  matricule: number | null;
  groupId: string | null;
  name: string | null;
  description: string | null;
  areaCode: string | null;
  email: string | null;
  internalSupportComment: string | null;
}

interface NewMatricules {
  matricules: NewMatricule[];
}

interface InsertResult {
  hasInsertedGroup: boolean;
  matricule: number | null;
  groupId: string | null;
  groupName: string | null;
  error: string | null;
}

interface InsertsResult {
  inserts: InsertResult[];
  // ApiError
  message: string | null;
}

interface MatriculeUpdate {
  matricule: number;
  groupId: string;
}

interface GroupUpdate {
  matricule: number;
  groupId: string;
}

interface Update {
  matriculeUpdate?: MatriculeUpdate;
  groupUpdate?: GroupUpdate;
}


if (window.document.getElementById(tableId)) {

  let table: Tabulator | null = null;
  let addTable: Tabulator | null = null;

  let groupList: Group[] = [];

  const ajaxUrl: string = jsRoutes.controllers.ApiController.franceServices().url;

  // CSRF Token
  const csrfTokenInput = <HTMLInputElement>document.querySelector('input[name=csrfToken]');
  const csrfToken: string = csrfTokenInput.value;

  const alerts = <HTMLElement | null>document.getElementById(alertsId);
  const addAlerts = <HTMLElement | null>document.getElementById(addAlertsId);

  const printAlerts = (alertsEl: HTMLElement | null, successMessages: string[], errorMessages: string[]) => {
    if (alertsEl) {
      let successMessagesHtml = '';
      if (successMessages.length > 0) {
        successMessagesHtml =
          '<div class="notification notification--success single--flex-wrap-wrap">' +
          successMessages.map((m) => '<div class="single--width-100pc">' + m + '</div>').join('') +
          '</div>';
      }

      let errorMessagesHtml = '';
      if (errorMessages.length > 0) {
        errorMessagesHtml =
          '<div class="notification notification--error single--flex-wrap-wrap">' +
          errorMessages.map((m) => '<div class="single--width-100pc">' + m + '</div>').join('') +
          '</div>';
      }

      alertsEl.innerHTML = '<div class="mdl-cell mdl-cell--12-col">' +
        errorMessagesHtml + successMessagesHtml +
        '</div>';
    }
  };

  //
  // Callbacks
  //

  const csvDownloadBtn = window.document.getElementById(downloadBtnCsv);
  if (csvDownloadBtn) {
    csvDownloadBtn.onclick = () => {
      const date = new Date().toLocaleDateString(
        'fr-fr',
        { year: 'numeric', month: 'numeric', day: 'numeric' }
      );
      const filename = 'France Services - ' + date;
      table?.download('csv', filename + '.csv');
    };
  }

  const xlsxDownloadBtn = window.document.getElementById(downloadBtnXlsx);
  if (xlsxDownloadBtn) {
    xlsxDownloadBtn.onclick = () => {
      const date = new Date().toLocaleDateString(
        'fr-fr',
        { year: 'numeric', month: 'numeric', day: 'numeric' }
      );
      const filename = 'France Services - ' + date;
      table?.download('xlsx', filename + '.xlsx', { sheetName: 'France Services' });
    };
  }

  const addCsvBtn = <HTMLElement | null>document.getElementById(addCsvBtnId);
  if (addCsvBtn) {
    addCsvBtn.addEventListener('click', (_) => {
      if (addTable) {
        const previousData: NewMatricule[] = addTable?.getData().map((row) => {
          return {
            'matricule': parseInt(row.matricule),
            'groupId': row.groupId?.toString(),
            'name': row.name?.toString(),
            'description': row.description?.toString(),
            'areaCode': row.areaCode?.toString(),
            'email': row.email?.toString(),
            'internalSupportComment': row.internalSupportComment?.toString(),
          };
        });

        // .import not in types
        (<any>addTable).import('csv', '.csv').then(() => {
          const importedData = addTable?.getData().map((row) => {
            let newRow = {
              'matricule': parseInt(row.matricule),
              'groupId': row.groupId?.toString(),
              'name': row.name?.toString(),
              'description': row.description?.toString(),
              'areaCode': row.areaCode?.toString(),
              'email': row.email?.toString(),
              'internalSupportComment': row.internalSupportComment?.toString(),
            };
            if (row.groupId != null && row.groupId !== '') {
              const group = groupList.find((g) => g.id === row.groupId);
              newRow.name = group?.name;
            }
            return newRow;
          }) || [];
          addTable?.setData(previousData.concat(importedData));
        });
      }
    });
  }

  const addTableCsvDownloadBtn = window.document.getElementById(addDownloadCsvBtnId);
  if (addTableCsvDownloadBtn) {
    addTableCsvDownloadBtn.onclick = () => {
      const date = new Date().toLocaleDateString(
        'fr-fr',
        { year: 'numeric', month: 'numeric', day: 'numeric' }
      );
      const filename = 'France Services - ' + date;
      addTable?.download('csv', filename + '.csv');
    };
  }

  const dedupBtn = window.document.getElementById(addDedupBtnId);
  if (dedupBtn) {
    dedupBtn.onclick = () => {
      if (table != null && addTable != null) {
        let matriculeToGroupId: Map<number, string> = new Map();
        let groupIdToMatricule: Map<string, number> = new Map();
        for (let row of table.getData()) {
          const matricule = parseInt(row.matricule);
          const groupId = row.groupId.toString();
          if (!isNaN(matricule)) {
            matriculeToGroupId.set(matricule, groupId);
            groupIdToMatricule.set(groupId, matricule);
          }
        }
        let successMessages: string[] = [];
        let errorMessages: string[] = [];
        let addTableMatriculeToGroupId: Map<number, string> = new Map();
        let addTableGroupIdToMatricule: Map<string, number> = new Map();
        for (let row of addTable.getRows()) {
          const matricule = parseInt(row.getData().matricule);
          const groupId = row.getData().groupId?.toString();
          let rowDeleted = false;
          // Dedup from main table
          if (matriculeToGroupId.has(matricule)) {
            const existingGroupId = matriculeToGroupId.get(matricule);
            if (existingGroupId === groupId) {
              successMessages.push(`Duplicat : matricule ${ matricule } groupe ${ groupId } supprimé`);
              row.delete();
              rowDeleted = true;
            } else {
              errorMessages.push(
                `Matricule ${ matricule } groupe ${ groupId } : déjà lié au groupe ${ existingGroupId }`
              );
            }
          } else if (groupIdToMatricule.has(groupId)) {
            const existingMatricule = groupIdToMatricule.get(groupId);
            errorMessages.push(
              `Groupe ${ groupId } matricule ${ matricule } : déjà lié au matricule ${ existingMatricule }`
            );
          }
          if (!rowDeleted) {
            // Dedup add table
            if (addTableMatriculeToGroupId.has(matricule)) {
              const existingGroupId = addTableMatriculeToGroupId.get(matricule);
              if (existingGroupId === groupId) {
                successMessages.push(`Duplicat : matricule ${ matricule } groupe ${ groupId } supprimé`);
                row.delete();
              } else {
                errorMessages.push(
                  `Table d'ajout : 2 groupes pour le matricule ${ matricule } : ` +
                  `${ existingGroupId } et ${ groupId }`
                );
              }
            } else if (addTableGroupIdToMatricule.has(groupId)) {
              const existingMatricule = addTableGroupIdToMatricule.get(groupId);
              errorMessages.push(
                `Table d'ajout : 2 matricules pour le groupe ${ groupId } : ` +
                `${ existingMatricule } et ${ matricule }`
              );
            }
            if (!isNaN(matricule)) {
              addTableMatriculeToGroupId.set(matricule, groupId);
            }
            if (groupId.length !== 0) {
              addTableGroupIdToMatricule.set(groupId, matricule);
            }
          }
        }

        printAlerts(addAlerts, successMessages, errorMessages);
      }
    };
  }

  const addLineBtn = <HTMLElement | null>document.getElementById(addLineBtnId);
  if (addLineBtn) {
    addLineBtn.addEventListener('click', (_) => {
      addTable?.addRow({});
    });
  }

  const uploadBtn = <HTMLElement | null>document.getElementById(addUploadBtnId);
  if (uploadBtn) {
    uploadBtn.addEventListener('click', (_) => {
      let data = addTable?.getData();
      let newMatricules: NewMatricule[] = [];
      if (data) {
        newMatricules = data.map((line) => {
          let groupId: string | null = null;
          if (line.groupId) {
            groupId = line.groupId.toString();
          }
          let update: NewMatricule = {
            matricule: parseInt(line.matricule),
            groupId,
            name: <string | null>line.name,
            description: <string | null>line.description,
            areaCode: <string | null>line.areaCode,
            email: <string | null>line.email,
            internalSupportComment: <string | null>line.internalSupportComment,
          };
          return update;
        });
      }
      const bodyData: NewMatricules = {
        'matricules': newMatricules,
      };
      fetch(
        jsRoutes.controllers.ApiController.addFranceServices().url,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json;charset=utf-8',
            'Csrf-Token': csrfToken
          },
          body: JSON.stringify(bodyData)
        }
      ).then((response) =>
        response
          .json()
          .then((result: InsertsResult) => { return { result, isError: !response.ok }; })
      ).then(({ result, isError }) => {
        let successMessages: string[] = [];
        let errorMessages: string[] = [];

        if (isError && result.message) {
          errorMessages.push(result.message);
        } else {
          if (result.inserts.length === 0) {
            successMessages.push('Aucune donnée sauvegardée.');
          } else {
            for (let i = 0; i < result.inserts.length; i++) {
              const insert = result.inserts[i];
              if (insert) {
                if (insert.error) {
                  errorMessages.push(`Ligne ${ i + 1 } : ${ insert.error }`);
                } else {
                  const groupLink = '<a href="' +
                    jsRoutes.controllers.GroupController.editGroup(insert.groupId).url +
                    '">' + insert.groupId + '</a>';
                  if (insert.hasInsertedGroup) {
                    successMessages.push(
                      `Nouveau groupe '${ insert.groupName }' ${ groupLink } ` +
                      `ajouté avec le matricule ${ insert.matricule }`
                    );
                    const groupName = insert.groupName;
                    if (groupName) {
                      const rows = addTable?.searchRows('name', '=', groupName);
                      if (rows) {
                        for (let row of rows) {
                          row.delete();
                        }
                      }
                    }
                  } else {
                    successMessages.push(`Ajout du matricule ${ insert.matricule } au groupe ${ groupLink }`);
                    const groupId = insert.groupId;
                    if (groupId) {
                      const rows = addTable?.searchRows('groupId', '=', groupId);
                      if (rows) {
                        for (let row of rows) {
                          row.delete();
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }

        printAlerts(addAlerts, successMessages, errorMessages);

        table?.setData(ajaxUrl);
      });
    });
  }

  const callUpdate = (update: Update, successMessage: string, onFailure: () => void) => fetch(
    jsRoutes.controllers.ApiController.updateFranceService().url,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json;charset=utf-8',
        'Csrf-Token': csrfToken
      },
      body: JSON.stringify(update)
    }
  ).then((response) => {
    if (response.ok) {
      printAlerts(alerts, [successMessage], []);
    } else {
      response
        .json()
        .then((result: ApiError) => {
          if (result.message) {
            printAlerts(alerts, [], [result.message]);
          } else {
            printAlerts(alerts, [], ['Une erreur est survenue dans la mise à jour']);
          }
          onFailure();
        })
        .catch((_e) => {
          printAlerts(alerts, [], ['Une erreur est survenue dans la mise à jour']);
          onFailure();
        });
    }
  }).catch((_e) => {
    printAlerts(
      alerts,
      [],
      ['Une erreur s\'est produite']
    );
    onFailure();
  });

  const callMatriculeUpdate = (groupId: string, matricule: number, successMessage: string, onFailure: () => void) =>
    callUpdate(
      { matriculeUpdate: { groupId, matricule } },
      successMessage,
      onFailure,
    );

  const callDelete = (matricule: number, onSuccess: () => void, onFailure: () => void) => fetch(
    jsRoutes.controllers.ApiController.deleteFranceService(matricule).url,
    {
      method: 'POST',
      headers: {
        'Csrf-Token': csrfToken
      },
    }
  ).then((response) => {
    if (response.ok) {
      printAlerts(alerts, [`Matricule ${ matricule } supprimé`], []);
      onSuccess();
    } else {
      response
        .json()
        .then((result: ApiError) => {
          if (result.message) {
            printAlerts(alerts, [], [result.message]);
          } else {
            printAlerts(alerts, [], ['Impossible de supprimer la ligne']);
          }
          onFailure();
        })
        .catch((_e) => {
          printAlerts(alerts, [], ['Impossible de supprimer la ligne']);
          onFailure();
        });
    }
  }).catch((_e) => {
    printAlerts(
      alerts,
      [],
      [`Une erreur s\'est produite pour supprimer le matricule ${ matricule }`]
    );
    onFailure();
  });


  //
  // Tables Config
  //

  const deleteColBase: ColumnDefinition = {
    title: '',
    field: '',
    hozAlign: 'center',
    headerSort: false,
    frozen: true,
    download: false,
  };

  const addTableDeleteColCellFormatter: Formatter =
    (cell) => {
      cell.getElement().classList.add('mdl-color--red-200');
      return '<i class="fas fa-close"></i>';
    };

  const addTableDeleteColCellClick: CellEventCallback =
    (_e, cell) => {
      cell.getRow().delete();
    };

  const matriculeColBase: ColumnDefinition = {
    title: 'Matricule',
    field: 'matricule',
    hozAlign: 'right',
    minWidth: 40,
    sorter: 'number',
    sorterParams: {
      alignEmptyValues: 'bottom',
    },
    bottomCalc: 'count',
    editor: 'number',
    editorParams: {
      min: 0
    },
  };

  const matriculeColEdited: CellEditEventCallback =
    (cell) => {
      const oldMatricule = cell.getOldValue();
      const matricule = cell.getValue();
      const groupId = <string>cell.getRow().getData().groupId;
      if (matricule === '') {
        if (oldMatricule != null) {
          callDelete(
            oldMatricule,
            () => {
              const organisation = cell.getRow().getData().organisation;
              if (organisation !== 'FS') {
                cell.getRow().delete();
              }
            },
            () => cell.restoreOldValue()
          );
        }
      } else {
        if (oldMatricule !== matricule) {
          callMatriculeUpdate(
            groupId,
            matricule,
            `Groupe ${ groupId } : changement du matricule ${ oldMatricule } par ${ matricule }`,
            () => cell.restoreOldValue()
          );
        }
      }
    };

  const groupListPromise = fetch(jsRoutes.controllers.UserController.search().url + '?groupsOnly=true')
    .then((response) => response.json())
    .then((data) => {
      const groups: Group[] = data.groups;
      const options = groups.map((group: Group) => {
        return {
          label: group.name,
          value: group.id
        };
      });
      groupList = groups;
      return options;
    });

  const addTableGroupColEdited: CellEditEventCallback =
    (cell) => {
      const groupId = cell.getValue();
      const group = groupList.find((g) => g.id === groupId);
      if (group) {
        cell.getRow().update({ 'name': group.name });
      }
    };

  const groupColBase: ColumnDefinition = {
    title: 'Groupe',
    field: 'groupId',
    headerFilter: 'input',
    headerSort: false,
    width: 100,
    bottomCalc: 'count',
    titleDownload: 'Identifiant Groupe A+',
  };

  const groupColEditorParams = {
    editor: 'list',
    editorParams: {
      autocomplete: true,
      clearable: true,
      values: groupListPromise,
      placeholderLoading: 'Chargement des groupes...',
    }
  };

  const groupCol: ColumnDefinition = Object.assign(groupColEditorParams, groupColBase);

  const nameCol: ColumnDefinition = {
    title: 'Nom',
    field: 'name',
    headerFilter: 'input',
    width: 300,
  };

  const descriptionCol: ColumnDefinition = {
    title: 'Description',
    field: 'description',
    headerFilter: 'input',
    maxWidth: 300,
  };

  const areasCol: ColumnDefinition = {
    title: 'Départements',
    field: 'areas',
    headerFilter: 'input',
    maxWidth: 300,
  };

  const organisationCol: ColumnDefinition = {
    title: 'Organisme',
    field: 'organisation',
    headerFilter: 'input',
  };

  const emailCol: ColumnDefinition = {
    title: 'BAL',
    field: 'email',
    headerFilter: 'input',
    minWidth: 100,
    maxWidth: 300,
  };

  const publicNoteCol: ColumnDefinition = {
    title: 'Description détaillée',
    field: 'publicNote',
    headerFilter: 'input',
    maxWidth: 300,
  };

  const addTableAreaCodeCol: ColumnDefinition = {
    title: 'Code Département',
    field: 'areaCode',
    headerFilter: 'input',
    editor: 'input',
  };

  const addTableInternalCommentCol: ColumnDefinition = {
    title: 'Commentaire interne',
    field: 'internalSupportComment',
    headerFilter: 'input',
    editor: 'input',
    maxWidth: 300,
  };

  const options: Options = {
    height: '50vh',
    langs: {
      'fr-fr': {
        'data': {
          'loading': 'Chargement',
          'error': 'Erreur',
        },
        'headerFilters': {
          'default': 'filtrer...'
        }
      }
    },
    columns: [
      Object.assign({ cellEdited: matriculeColEdited }, matriculeColBase),
      groupColBase,
      nameCol,
      areasCol,
      emailCol,
      organisationCol,
      descriptionCol,
      publicNoteCol,
    ],
    initialSort: [{ column: 'matricule', dir: 'asc' }],
    ajaxURL: ajaxUrl,
    ajaxResponse(_url, _params, response) {
      return response.franceServices;
    }
  };
  table = new TabulatorFull('#' + tableId, options);

  const addOptions: Options = {
    langs: {
      'fr-fr': {
        'data': {
          'loading': 'Chargement',
          'error': 'Erreur',
        },
        'headerFilters': {
          'default': 'filtrer...'
        }
      }
    },
    columns: [
      Object.assign({}, matriculeColBase),
      Object.assign({ cellEdited: addTableGroupColEdited }, groupCol),
      Object.assign({ editor: 'input' }, nameCol),
      Object.assign({ editor: 'input' }, emailCol),
      addTableAreaCodeCol,
      Object.assign({ editor: 'input' }, descriptionCol),
      addTableInternalCommentCol,
      Object.assign(
        { cellClick: addTableDeleteColCellClick, formatter: addTableDeleteColCellFormatter },
        deleteColBase
      ),
    ],
  };
  addTable = new TabulatorFull('#' + addTableId, addOptions);
}
