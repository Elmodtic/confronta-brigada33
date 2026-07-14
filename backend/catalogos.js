// catalogos.js — datos oficiales de grados y unidades (fuente única de verdad).

// Grados de la Fuerza Terrestre, del más alto al más bajo. [nombre, abreviatura]
const GRADOS = [
  ['General de Ejército', 'GRAE'],
  ['General de División', 'GRAD'],
  ['General de Brigada', 'GRAB'],
  ['Coronel', 'CRNL'],
  ['Teniente Coronel', 'TCRN'],
  ['Mayor', 'MAYO'],
  ['Capitán', 'CAPT'],
  ['Teniente', 'TNTE'],
  ['Subteniente', 'SUBT'],
  ['Suboficial Mayor', 'SUBM'],
  ['Suboficial Primero', 'SUBP'],
  ['Suboficial Segundo', 'SUBS'],
  ['Sargento Primero', 'SGTP'],
  ['Sargento Segundo', 'SGTS'],
  ['Cabo Primero', 'CBOP'],
  ['Cabo Segundo', 'CBOS'],
  ['Soldado', 'SLDO'],
];

// Unidades de la brigada. [nombre, siglas]
const UNIDADES = [
  ['COMANDO DE APOYO LOGÍSTICO ELECTRÓNICO NRO. 33', 'CALE33'],
  ['BATALLÓN DE COMUNICACIONES NRO. 98', 'BC98'],
  ['COMANDO Y ESTADO MAYOR NRO. 33', 'CEM33'],
  ['CENTRO DE METROLOGÍA DE LA FUERZA TERRESTRE', 'C.MET.F.T'],
  ['GRUPO DE CIBERDEFENSA Y GUERRA ELECTRÓNICA NRO. 97', 'GRUCIGE97'],
  ['COMPAÑÍA POLICÍA MILITAR NRO. 33', 'CPM33'],
  ['POLICLÍNICO NRO. 33', 'POL33'],
  ['COMPAÑÍA LOGÍSTICA NRO. 33', 'CLOG33'],
  ['BATALLÓN DE INFORMÁTICA NRO. 99', 'BINFO99'],
];

module.exports = { GRADOS, UNIDADES };
