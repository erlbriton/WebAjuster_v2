const SLAVE_ADDRESS=0x01;
const REGISTER_ADDR=0x002D;

let serial, parser;
let lastPacketTime=0;
let packetCount=0;
let isConnected=false;

window.connectToDevice=async function(){
    try{
        if(isConnected){
            console.log("[Main] Already connected");
            return;
        }

        // 🔥 ИНИЦИАЛИЗАЦИЯ КАНАЛОВ
        if(window.oscilloInit){
            window.oscilloInit("testId", "oscCanvas", 0, 1100);
            window.oscilloInit("testId2", "oscCanvas", 0, 1100);
            console.log("[Main] Channels initialized!");
        }

        serial=new SerialConnection();
        parser=new ModbusParser();

        console.log("[Main] Connecting...");
        await serial.connect(115200);
        console.log("[Main] Connected!");

        isConnected=true;
        lastPacketTime=performance.now();
        packetCount=0;

        readLoop();
        writeLoop();
    }catch(error){
        console.error("[Main] Error:",error.message);
        alert(error.message);
    }
};

async function readLoop(){
    console.log("[Main] readLoop started");
    try{
        while(isConnected){
            const chunk=await serial.readChunk();
            if(!chunk)break;

            parser.appendData(chunk);
            let packetData=parser.parsePacket();

            while(packetData!==null){
                handleValidPacket(packetData);
                packetData=parser.parsePacket();
            }
        }
    }catch(error){
        console.error("[Main] readLoop error:",error.message);
    }
}

async function writeLoop(){
    console.log("[Main] writeLoop started");
    while(isConnected){
        try{
            const body=new Uint8Array([
                SLAVE_ADDRESS,
                0x03,
                (REGISTER_ADDR>>8)&0xFF,
                REGISTER_ADDR&0xFF,
                0x00,
                0x02
            ]);

            let crc=0xFFFF;
            for(let pos=0;pos<body.length;pos++){
                crc^=body[pos];
                for(let i=8;i!==0;i--){
                    if((crc&0x0001)!==0){
                        crc>>=1;
                        crc^=0xA001;
                    }else{
                        crc>>=1;
                    }
                }
            }

            const finalPacket=new Uint8Array(8);
            finalPacket.set(body,0);
            finalPacket[6]=crc&0xFF;
            finalPacket[7]=(crc>>8)&0xFF;

            await serial.write(finalPacket);
        }catch(error){
            console.error("[Main] writeLoop error:",error.message);
        }

        await new Promise(res=>setTimeout(res,5)); // 5ms для плавности на Windows
    }
}

function handleValidPacket(packetData){
    const currentTime=performance.now();
    const interval=currentTime-lastPacketTime;
    lastPacketTime=currentTime;
    packetCount++;

    let val1=Array.isArray(packetData)?packetData[0]:packetData;
    let val2=Array.isArray(packetData)?packetData[1]:packetData;

    if(packetCount>1 && packetCount%10===0){
        console.log(`[Main] R1:${val1} R2:${val2}`);
    }

    // 🔥 ЛОГИРОВАНИЕ (добавить это!)
    console.log(`[Main] Push: testId=${val1}, testId2=${val2}`);
    console.log(`[Main] window.oscilloPush exists: ${typeof window.oscilloPush !== 'undefined'}`);

    // Отправляем в осциллограф
    if(window.oscilloPush){
        window.oscilloPush("testId",val1,0,1100);
        window.oscilloPush("testId2",val2,0,1100);
        console.log(`[Main] oscilloPush called!`);
    } else {
        console.error(`[Main] oscilloPush NOT FOUND!`);
    }
}

console.log("[Main] main.js LOADED OK");