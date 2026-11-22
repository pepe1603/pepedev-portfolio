// ======================================================================
// ESTILO COMUN DE LAS CARDS INTERACTIVAS
//
// Las cards publicas de proyectos y certificados y las cards de los listados
// del panel comparten el mismo lenguaje visual: al pasar el raton la card se
// eleva 4 px, el borde pasa a primario y aparece una sombra, todo con una
// transicion de 300 ms. Vive aqui para que las dos caras del sitio no se
// separen con el tiempo.
//
// `group` en la raiz es lo que permite que los hijos (titulo, flecha,
// miniatura, badges) reaccionen con `group-hover:`.
//
// `motion-reduce` quita el movimiento y la transicion, pero no los cambios de
// color: quien pide menos movimiento sigue viendo que la card es interactiva.
//
// OJO: son clases estaticas a proposito. Tailwind las detecta al escanear este
// archivo; si se construyeran concatenando trozos, no las generaria.
// ======================================================================
export const CARD_INTERACTIVA = 'group h-full overflow-hidden rounded-2xl border border-default bg-default transition-[border-color,transform,box-shadow] duration-300 ease-out hover:-translate-y-1 hover:border-primary/50 hover:shadow-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary motion-reduce:transform-none motion-reduce:transition-none'

/**
 * Foco visible para el enlace o boton que envuelve una card interactiva.
 * Va en el elemento que recibe el foco (el enlace), no en la card, porque el
 * outline debe quedar fuera de la tarjeta y no lo recorta su overflow.
 */
export const CARD_FOCO = 'rounded-2xl focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary'
