var cron = require('node-cron');
var sudo = require('sudo-js');
//also install module "uuid"
sudo.setPassword('1fads23');





sudo.check(function(valid) {
    console.log('password valid : ', valid);
});
















//every friday at midnight
cron.schedule('0 0 * * FRI', () => {
  console.log('its friday and i');





});



//every monday at midnight
cron.schedule('0 0 * * MON', () => {
    console.log('its monday and i');
    
  
  
  
    
  });