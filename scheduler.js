var cron = require('node-cron');
// var sudo = require('sudo-js');
const { exec, execFile, execSync } = require('child_process');
const { stderr, stdout } = require('process');
const { Client, GatewayIntentBits, MessageEmbed } = require('discord.js');
const { client } = new Client({ intents: [GatewayIntentBits.FLAGS.GUILDS] });
const fs = require('fs');
//also install module "uuid"


// sudo.setPassword('123');


// /home/brody/Serverfiles/Erupe/bin/quests 
//is the folder to movec quests into


// sudo.check(function(valid) {
//     console.log('password valid : ', valid);
// });



client.once('ready', () => {
	console.log('discord logged in!');
});

// exec("konsole")
exec('konsole -e "tmuxinator start gameserver"')





async function friday() {
  exec("tmuxinator stop gameserver")
  exec("cp /home/brody/eventquests/* /home/brody/Serverfiles/Erupe/bin/quests -v", (err, stdout, stderr) => {console.log(stdout)})
  exec('konsole -e "tmuxinator start gameserver"')
}

async function monday() {
  exec("tmuxinator stop gameserver")
  exec("cp /home/brody/normalquests/* /home/brody/Serverfiles/Erupe/bin/quests -v", (err, stdout, stderr) => {console.log(stdout)})
  exec('konsole -e "tmuxinator start gameserver"')
}


//  0 0 * * FRI
//every friday at midnight

cron.schedule('37 16 * * *', () => {
  console.log('its friday and i');
  //move edited quest files into the folder
  //    cp /home/brody/eventquests/* /home/brody/Serverfiles/Erpue/bin/quests


  async function friday() {
    execSync("tmuxinator stop gameserver")
    console.log('stopped game server')
    execSync("cp /home/brody/eventquests/* /home/brody/Serverfiles/Erupe/bin/quests -v", (err, stdout, stderr) => {console.log(stdout)})
    console.log('copied friday')
    exec('konsole -e "tmuxinator start gameserver"')
    console.log('started gameserver')
  }


  console.log('friday, before restarting')
  friday()

  client.channels.cache.get('988518834304610304').messages.fetch().then(fetched => {
    client.channels.cache.get('988518834304610304').bulkDelete(fetched)});

  client.channels.cache.get('988518834304610304').send('The Raviente event is now happening!')
  console.log('which one?')
  exec("cat /home/brody/Serverfiles/Erupe/bin/quests/whichone", (err, stdout, stderr) => {console.log(stdout)})

});

//pee poop
//  0 0 * * MON
//every monday at midnight

cron.schedule('38 16 * * *', () => {
    console.log('its monday and i');

    //move original quests into quest folder
    //    cp /home/brody/normal/* /home/brody/Serverfiles/Erpue/bin/quests
    // exec("cp /home/brody/normal/* /home/brody/Serverfiles/Erpue/bin/quests -v")

    async function monday() {
      
      execSync("tmuxinator stop gameserver")
      console.log('stopped game server')
      execSync("cp /home/brody/normalquests/* /home/brody/Serverfiles/Erupe/bin/quests -v", (err, stdout, stderr) => {console.log(stdout)})
      console.log('copied monday')
      exec('konsole -e "tmuxinator start gameserver"')
      console.log('started gameserver')
    }




    console.log('monday, before restarting')
    monday()
   
    client.channels.cache.get('988518834304610304').messages.fetch().then(fetched => {
      client.channels.cache.get('988518834304610304').bulkDelete(fetched)}); 

    client.channels.cache.get('988518834304610304').send('The Raviente event is currently inactive.')
    

    console.log('which one?')
    exec("cat /home/brody/Serverfiles/Erupe/bin/quests/whichone", (err, stdout, stderr) => {console.log(stdout)})

  });

// 0 0 * * *

cron.schedule('0 0 * * *', () => {
  console.log("running thescript to randomize")

  client.channels.cache.get('988518785940082768').messages.fetch().then(fetched => {
    client.channels.cache.get('988518785940082768').bulkDelete(fetched)});


 // exec('konsole --workdir /home/brody/mhrandomizer -e "node randomizer.js"')
   execSync('konsole --workdir /home/brody/mhrandomizer -e "node randomizer.js"')

   .then(
     fs.readFile('/home/brody/mhrandomizer/discord.json', (err, data) => {
      // console.log(data) 
      if (err) throw err;
        let discord1 = JSON.parse(data)
        // console.log(discord1)

        const embed = new MessageEmbed();
        embed.addFields(discord1);
        // console.log(embed)
        client.channels.cache.get('988518785940082768').send({ embeds: [embed]});

     })
   
  
     ,fs.readFile('/home/brody/mhrandomizer/discord2.json', (err, data) => {
       if (err) throw err;
        let discord2 = JSON.parse(data)
        // console.log(discord2)

        const embed = new MessageEmbed();
        embed.addFields(discord2);
        // console.log(embed)
        client.channels.cache.get('988518785940082768').send({ embeds: [embed]});

     })
   
  
     ,fs.readFile('/home/brody/mhrandomizer/discord3.json', (err, data) => {
       if (err) throw err;
        let discord3 = JSON.parse(data)
        // console.log(discord3)

        const embed = new MessageEmbed();
        embed.addFields(discord3);
        // console.log(embed)
        client.channels.cache.get('988518785940082768').send({ embeds: [embed]});

     })
   
  
     ,fs.readFile('/home/brody/mhrandomizer/discord4.json', (err, data) => {
       if (err) throw err;
        let discord4 = JSON.parse(data)
        // console.log(discord4)

        const embed = new MessageEmbed();
        embed.addFields(discord4);
        // console.log(embed)
        client.channels.cache.get('988518785940082768').send({ embeds: [embed]});

     })
   
  
     ,fs.readFile('/home/brody/mhrandomizer/discord5.json', (err, data) => {
       if (err) throw err;
        let discord5 = JSON.parse(data)
        // console.log(discord5)

        const embed = new MessageEmbed();
        embed.addFields(discord5);
        // console.log(embed)
        client.channels.cache.get('988518785940082768').send({ embeds: [embed]});

     })
   
  
     ,fs.readFile('/home/brody/mhrandomizer/discord6.json', (err, data) => {
       if (err) throw err;
        let discord6 = JSON.parse(data)
        // console.log(discord6)

        const embed = new MessageEmbed();
        embed.addFields(discord6);
        // console.log(embed)
        client.channels.cache.get('988518785940082768').send({ embeds: [embed]});

     })
   
  
     ,fs.readFile('/home/brody/mhrandomizer/discord7.json', (err, data) => {
       if (err) throw err;
        let discord7 = JSON.parse(data)
        // console.log(discord7)

        const embed = new MessageEmbed();
        embed.addFields(discord7);
        // console.log(embed)
        client.channels.cache.get('988518785940082768').send({ embeds: [embed]});

     })
   
  
     ,fs.readFile('/home/brody/mhrandomizer/discord8.json', (err, data) => {
       if (err) throw err;
        let discord8 = JSON.parse(data)
        // console.log(discord8)

        const embed = new MessageEmbed();
        embed.addFields(discord8);
        // console.log(embed)
        client.channels.cache.get('988518785940082768').send({ embeds: [embed]});

     })
   
  
     ,fs.readFile('/home/brody/mhrandomizer/discord9.json', (err, data) => {
       if (err) throw err;
        let discord9 = JSON.parse(data)
        // console.log(discord9)

        const embed = new MessageEmbed();
        embed.addFields(discord9);
        // console.log(embed)
        client.channels.cache.get('988518785940082768').send({ embeds: [embed]});

     })
   )




  

    
    

})

client.login(process.env.TOKEN)