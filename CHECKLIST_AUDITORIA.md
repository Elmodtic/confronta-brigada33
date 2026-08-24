# CHECKLIST_AUDITORIA.md — Controles mínimos de auditoría de configuración

Este checklist se aplica en cada Pull Request antes de aprobar el merge a `main`.

## 1. Auditoría física (elementos de configuración)
- [ ] `README.md` actualizado y refleja el estado real del proyecto.
- [ ] `.gitignore` cubre `node_modules/`, `.env`, `build/`, artefactos de Android.
- [ ] No hay secretos ni credenciales reales versionados (`git ls-files` no debe listar `.env`, `Credenciales.txt`, etc.).
- [ ] `backend/.env.example` existe y está sincronizado con las variables reales usadas en `server.js`.
- [ ] `LICENSE` presente.
- [ ] Documentación técnica (`docs/`) refleja los cambios del PR, si aplica.

## 2. Auditoría funcional (requisitos)
- [ ] El PR referencia el/los requisito(s) (REQ-xx) o issue(s) que valida o modifica.
- [ ] Si el cambio afecta un endpoint o flujo existente, se re-ejecutan y documentan los criterios de aceptación relevantes.
- [ ] La evidencia (capturas, salida de `curl`, pasos de prueba) queda adjunta en el issue o en el PR.

## 3. Trazabilidad
- [ ] El PR está vinculado a un ISSUE (`Closes #N` / `Refs #N`).
- [ ] Los commits usan la convención `tipo: descripción (#issue)` (ver README, sección "Convención de commits").
- [ ] El historial no mezcla cambios no relacionados en un mismo commit.

## 4. Integridad
- [ ] Revisión del PR (mínimo el propio autor certifica el checklist; idealmente un segundo revisor cuando el equipo lo permite).
- [ ] No quedan archivos sueltos, temporales o de depuración en el diff.
- [ ] El PR fue mergeado desde una rama `feature/`, `audit/`, `docs/` o `fix/` hacia `main` (no commits directos a `main` para cambios no triviales).

## 5. Línea base y release
- [ ] El release se genera desde `main` después del merge (no desde una rama de feature).
- [ ] El tag sigue SemVer (`vMAJOR.MINOR.PATCH`).
- [ ] Las release notes listan: qué cambió, cómo validarlo, e issues/PR relacionados.

## 6. Entrega
- [ ] Existen instrucciones de arranque/despliegue actualizadas (`docs/GUIA_ARRANQUE.md`, `iniciar_backend.bat`).
- [ ] Se documenta cómo verificar que el cambio funciona (pasos manuales o automatizados).
