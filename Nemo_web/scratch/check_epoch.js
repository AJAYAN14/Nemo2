
const date = new Date("2026-04-19T21:57:17+08:00");
const resetHour = 4;

const localHour = date.getHours();
const targetDate = new Date(date);

if (localHour < resetHour) {
  targetDate.setDate(targetDate.getDate() - 1);
}

const year = targetDate.getFullYear();
const month = targetDate.getMonth() + 1;
const day = targetDate.getDate();

const d = new Date(year, month - 1, day, 12, 0, 0); 
const epochDay = Math.floor(d.getTime() / 86400000);

console.log("Date:", date.toISOString());
console.log("Local Hour:", localHour);
console.log("Target Date:", targetDate.toISOString());
console.log("Epoch Day:", epochDay);
