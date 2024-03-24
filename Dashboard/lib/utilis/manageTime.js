let startTime;
let elapsedTime = 0;
let timerInterval;

const timeDisplay = document.getElementById('timeDisplay');
const actualTimeDisplay = document.getElementById('actualTime');
const startButton = document.getElementById('startButton');
const stopButton = document.getElementById('stopButton');
const resetButton = document.getElementById('resetButton');

function startTimer() {
  startTime = Date.now() - elapsedTime;
  timerInterval = setInterval(updateTimeDisplay, 1000);
  startButton.disabled = true;
  updateActualTime();
}

function stopTimer() {
  clearInterval(timerInterval);
  startButton.disabled = false;
}

function resetTimer() {
  clearInterval(timerInterval);
  elapsedTime = 0;
  updateTimeDisplay();
  startButton.disabled = false;
}

function updateTimeDisplay() {
  const currentTime = Date.now();
  elapsedTime = currentTime - startTime;
  const formattedTime = formatTime(elapsedTime);
  timeDisplay.textContent = formattedTime;
}

function formatTime(time) {
  const hours = Math.floor(time / (1000 * 60 * 60));
  const minutes = Math.floor((time % (1000 * 60 * 60)) / (1000 * 60));
  const seconds = Math.floor((time % (1000 * 60)) / 1000);
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}

function pad(num) {
  return num.toString().padStart(2, '0');
}

function updateActualTime() {
  const currentTime = new Date();
  const hours = pad(currentTime.getHours());
  const minutes = pad(currentTime.getMinutes());
  const seconds = pad(currentTime.getSeconds());
  actualTimeDisplay.textContent = `Current Time: ${hours}:${minutes}:${seconds}`;
}

// Initial call to update actual time
updateActualTime();

startButton.addEventListener('click', startTimer);
stopButton.addEventListener('click', stopTimer);
resetButton.addEventListener('click', resetTimer);
