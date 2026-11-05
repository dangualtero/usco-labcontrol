// Aplica el tema guardado (o el del sistema) antes de que React cargue, para evitar un destello blanco.
(function () {
  try {
    var g = localStorage.getItem('usco.labcontrol.tema');
    var oscuro = g ? g === 'oscuro' : window.matchMedia('(prefers-color-scheme: dark)').matches;
    if (oscuro) document.documentElement.classList.add('dark');
  } catch (e) {}
})();
