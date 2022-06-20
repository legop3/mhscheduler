var cron = require('node-cron');
// var sudo = require('sudo-js');
const { exec, execFile, execSync } = require('child_process');
const { stderr, stdout } = require('process');
const { Client, Intents, MessageEmbed } = require('discord.js');
const client = new Client({ intents: [Intents.FLAGS.GUILDS] });
const fs = require('fs');
//also install module "uuid"


// sudo.setPassword('123');


// /home/brody/serverfiles/Erupe/bin/quests 
//is the folder to movec quests into


// sudo.check(function(valid) {
//     console.log('password valid : ', valid);
// });



client.once('ready', () => {
	console.log('Ready!');
});

// exec("konsole")
exec('konsole -e "tmuxinator start gameserver"')





async function friday() {
  exec("tmuxinator stop gameserver")
  exec("cp /home/brody/eventquests/* /home/brody/serverfiles/Erupe/bin/quests -v", (err, stdout, stderr) => {console.log(stdout)})
  exec('konsole -e "tmuxinator start gameserver"')
}

async function monday() {
  exec("tmuxinator stop gameserver")
  exec("cp /home/brody/normalquests/* /home/brody/serverfiles/Erupe/bin/quests -v", (err, stdout, stderr) => {console.log(stdout)})
  exec('konsole -e "tmuxinator start gameserver"')
}


//  0 0 * * FRI
//every friday at midnight
cron.schedule('0 0 * * FRI', () => {
  console.log('its friday and i');
  //move edited quest files into the folder
  //    cp /home/brody/eventquests/* /home/brody/serverfiles/Erpue/bin/quests
  friday()


});


//  0 0 * * MON
//every monday at midnight
cron.schedule('0 0 * * MON', () => {
    console.log('its monday and i');
    //move original quests into quest folder
    //    cp /home/brody/normal/* /home/brody/serverfiles/Erpue/bin/quests
    // exec("cp /home/brody/normal/* /home/brody/serverfiles/Erpue/bin/quests -v")
    monday()
  
    
  });

// 0 0 * * *
cron.schedule('* * * * *', () => {
  console.log("running the fuckuing thing to fuck your mom")


  exec('konsole --workdir /home/brody/mhrandomizer -e "node randomizer.js"')
  // execSync('konsole --workdir /home/brody/mhrandomizer -e "node randomizer.js"')

  // .then(
  //   fs.readFile('/home/brody/mhrandomizer/discord.json', (err, data) => {
  //     if (err) throw err;
  //     // let discord = JSON.parse(data)
  //     // console.log(discord)

  //     // const embed = new MessageEmbed();
  //     // embed.addFields(discord);
  //     // console.log(embed)
  //     // client.channels.cache.get('988518785940082768').send(discord.toString())

  //   })
  // )


  

    
    

})

client.login(process.env.TOKEN)
