export default defineAppConfig({
  ui: {
    // ======================================================================
    // COLOR DE MARCA
    // Este es el unico lugar donde se cambia el color de identidad.
    // Acepta cualquier color de Tailwind: red, blue, emerald, amber, violet...
    // El tema claro usa el shade 500 y el oscuro el 400 de forma automatica.
    //
    // Si necesitas un color que no existe en Tailwind, declaralo en
    // app/assets/css/main.css con @theme static y usalo aqui por su nombre.
    //
    // secondary y neutral son los tonos de apoyo. Cambiarlos es opcional.
    // ======================================================================
    colors: {
      primary: 'red',
      secondary: 'zinc',
      neutral: 'zinc'
    }
  }
})
