import { BirthdaysStore } from "./birthdays";

// `npm run birthdays -w @simple/hub -- add "Marie" --day 03-14 [--year 1950]`   `... -- list`   `... -- remove <identifiant>`
const store = new BirthdaysStore(process.env.SIMPLE_DATA_DIR ?? "./data");
const [command, ...rest] = process.argv.slice(2);

function option(name: string): string | undefined {
  const i = rest.indexOf(`--${name}`);
  return i >= 0 ? rest[i + 1] : undefined;
}

if (command === "add") {
  const name = rest[0];
  const day = option("day");
  const year = option("year");
  if (!name || name.startsWith("--") || !day) {
    console.error('Usage : add "Prénom" --day MM-JJ [--year AAAA]   (exemple : add "Marie" --day 03-14 --year 1950)');
    process.exit(1);
  }
  try {
    const b = store.add({ name, day, ...(year ? { year: Number(year) } : {}) });
    console.log(`Anniversaire ${b.id} ajouté : ${b.name}, le ${b.day.slice(3)}/${b.day.slice(0, 2)}`);
  } catch (e) {
    console.error(`Anniversaire refusé : ${e instanceof Error ? e.message : String(e)}`);
    process.exit(1);
  }
} else if (command === "remove" && rest[0]) {
  if (store.remove(rest[0])) console.log("Anniversaire supprimé.");
  else {
    console.error("Aucun anniversaire avec cet identifiant.");
    process.exit(1);
  }
} else {
  const list = store.list();
  if (list.length === 0) console.log("Aucun anniversaire.");
  for (const b of list) console.log(`${b.id}  ${b.day.slice(3)}/${b.day.slice(0, 2)}  ${b.name}${b.year ? ` (${b.year})` : ""}`);
}
