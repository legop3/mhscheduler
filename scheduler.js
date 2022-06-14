var cron = require('node-cron');
var sudo = require('sudo-js');
const { exec } = require('child_process');
//also install module "uuid"


sudo.setPassword('123');


// /home/brody/serverfiles/Erupe/bin/quests 
//is the folder to movec quests into


sudo.check(function(valid) {
    console.log('password valid : ', valid);
});






exec("konsole")








//  0 0 * * FRI
//every friday at midnight
cron.schedule('0 0 * * FRI', () => {
  console.log('its friday and i');
  //move edited quest files into the folder
  //    cp /home/brody/eventquests/* /home/brody/serverfiles/Erpue/bin/quests
  exec("cp /home/brody/eventquests/* /home/brody/serverfiles/Erpue/bin/quests -v")



});


//  0 0 * * MON
//every monday at midnight
cron.schedule('0 0 * * MON', () => {
    console.log('its monday and i');
    //move original quests into quest folder
    //    cp /home/brody/normal/* /home/brody/serverfiles/Erpue/bin/quests
    exec("cp /home/brody/normal/* /home/brody/serverfiles/Erpue/bin/quests -v")
  
  
    
  });