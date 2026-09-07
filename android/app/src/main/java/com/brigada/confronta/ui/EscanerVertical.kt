package com.brigada.confronta.ui

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Escáner de QR bloqueado en vertical.
 *
 * La pantalla de captura que trae la librería sigue el sensor y acaba
 * girando el teléfono a horizontal a media lectura. En el comedor eso
 * estorba: el ranchero sostiene el teléfono con una mano mientras el
 * comensal le muestra el suyo.
 *
 * La orientación se fija en el manifiesto (`screenOrientation="portrait"`);
 * esta clase existe solo para poder declararla allí, porque la de la
 * librería ya viene registrada con su propia configuración.
 */
class EscanerVertical : CaptureActivity()
