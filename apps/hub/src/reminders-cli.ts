import { RemindersStore } from "./reminders";

// `npm run reminders -w @presence/hub -- add "Prendre le médicament" --at 08:00 [--days lun,mer,ven | --date 2026-10-12]`
//  `... -- list`   `... -- remove <identifiant>`
const store = new RemindersStore(process.env.PRESENCE_DATA_DIR ?? "./data");
const [command, ...rest] = process.argv.slice(2);
const DAYS = ["dim", "lun", "mar", "mer", "jeu", "ven", "sam"];

function option(name: string): string | undefined {
  const i = rest.indexOf(`--${name}`);
  return i >= 0 ? rest[i + 1] : undefined;
}

if (command === "add") {
  const title = rest[0];
  const at = option("at");
  const date = option("date");
  const dayNames = option("days")?.split(",").map((d) => d.trim().toLowerCase().slice(0, 3));
  const days = dayNames?.map((d) => DAYS.indexOf(d));
  if (!title || title.startsWith("--") || !at || days?.includes(-1)) {
    console.error('Usage : add "Titre" --at 08:00 [--days lun,mer | --date 2026-10-12]  (jours : dim lun mar mer jeu ven sam)');
    process.exit(1);
  }
  try {
    const r = store.add({ title, at, ...(date ? { date } : {}), ...(days ? { days } : {}) });
    console.log(`Rappel ${r.id} ajouté : ${r.at} ${r.title}`);
  } catch (e) {
    console.error(`Rappel refusé : ${e instanceof Error ? e.message : String(e)}`);
    process.exit(1);
  }
} else if (command === "remove" && rest[0]) {
  if (store.remove(rest[0])) console.log("Rappel supprimé.");
  else {
    console.error("Aucun rappel avec cet identifiant.");
    process.exit(1);
  }
} else {
  const list = store.list();
  if (list.length === 0) console.log("Aucun rappel.");
  for (const r of list) {
    const when = r.date ?? (r.days ? r.days.map((d) => DAYS[d]).join(",") : "tous les jours");
    console.log(`${r.id}  ${r.at}  ${when}  ${r.title}`);
  }
}
